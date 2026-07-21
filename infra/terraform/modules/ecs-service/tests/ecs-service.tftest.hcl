# Structural tests for the ecs-service module (TECH-141). No real AWS
# account is contacted — the AWS provider is fully mocked
# (mock_provider "aws" {}); `command = apply` never creates anything real,
# no task ever runs, and no image is ever pulled.
#
# Criteria 19, 21, 22, 23 (no autoscaling, no API Gateway, no Cognito, no
# VPC Link) are NOT expressed as runtime assertions — they are structural
# facts about this module's own main.tf (no aws_appautoscaling_*,
# aws_api_gateway_*, aws_cognito_*, or aws_api_gateway_vpc_link resource is
# declared anywhere in it), confirmed by reading the source, not something
# a mock apply can meaningfully differ on.

mock_provider "aws" {}

override_resource {
  target = aws_lb.internal
  values = {
    arn      = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/sipsa-production-internal-alb/abc123"
    dns_name = "internal-sipsa-production-alb-123456789.us-east-1.elb.amazonaws.com"
    zone_id  = "Z35SXDOTRQ7X7K"
  }
}

override_resource {
  target = aws_lb_target_group.app
  values = {
    arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/sipsa-production-app/abc123"
  }
}

override_resource {
  target = aws_lb_listener.http
  values = {
    arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:listener/app/sipsa-production-internal-alb/abc123/def456"
  }
}

override_data {
  target = data.aws_vpc.selected
  values = {
    cidr_block = "10.40.0.0/16"
  }
}

variables {
  project_name = "sipsa"
  environment  = "production"
  common_tags = {
    Project     = "sipsa"
    Environment = "production"
    Owner       = "test-owner"
    ManagedBy   = "terraform"
    Repository  = "dalejandrov/sipsa"
    CostCenter  = "test-cost-center"
  }
  vpc_id                 = "vpc-0123456789abcdef0"
  private_app_subnet_ids = ["subnet-app-a", "subnet-app-b"]
  ecs_cluster_id         = "arn:aws:ecs:us-east-1:123456789012:cluster/sipsa-production-cluster"
  task_definition_arn    = "arn:aws:ecs:us-east-1:123456789012:task-definition/sipsa-production-app:1"
  container_name         = "sipsa-app"
  container_port         = 8080
  rds_security_group_id  = "sg-rds-example"
}

# 1-3: ALB is internal, in the private application subnets, never
# internet-facing.
run "alb_is_internal_and_in_private_app_subnets" {
  command = apply

  assert {
    condition     = aws_lb.internal.internal == true
    error_message = "The ALB must be internal (never internet-facing)."
  }

  assert {
    condition     = toset(aws_lb.internal.subnets) == toset(["subnet-app-a", "subnet-app-b"])
    error_message = "The ALB must use exactly the private application subnets it was given."
  }
}

# 4: Target group uses IP target type.
run "target_group_uses_ip_target_type" {
  command = apply

  assert {
    condition     = aws_lb_target_group.app.target_type == "ip"
    error_message = "The target group must use target_type = ip (required for awsvpc Fargate tasks)."
  }
}

# 5: Listener is HTTP, internal only.
run "listener_is_http" {
  command = apply

  assert {
    condition     = aws_lb_listener.http.protocol == "HTTP" && aws_lb_listener.http.port == 80
    error_message = "The listener must be HTTP on port 80 (no ACM certificate exists yet; the ALB is internal-only)."
  }
}

# 6: Health check is correctly configured.
run "health_check_is_correct" {
  command = apply

  assert {
    condition     = aws_lb_target_group.app.health_check[0].path == "/actuator/health"
    error_message = "The default health check path must be /actuator/health."
  }

  assert {
    condition     = aws_lb_target_group.app.health_check[0].matcher == "200"
    error_message = "The default health check matcher must be 200."
  }

  assert {
    condition     = aws_lb_target_group.app.health_check[0].protocol == "HTTP"
    error_message = "The health check protocol must be HTTP."
  }
}

# 7-8: ECS Service is Fargate, desired_count defaults to 1.
run "ecs_service_is_fargate_with_desired_count_one" {
  command = apply

  assert {
    condition     = aws_ecs_service.app.launch_type == "FARGATE"
    error_message = "The ECS Service must use the FARGATE launch type."
  }

  assert {
    condition     = aws_ecs_service.app.desired_count == 1
    error_message = "desired_count must default to 1 (the in-process scheduler is not safe for multiple replicas)."
  }
}

# 9: Deployment circuit breaker with rollback.
run "deployment_circuit_breaker_enabled_with_rollback" {
  command = apply

  assert {
    condition     = aws_ecs_service.app.deployment_circuit_breaker[0].enable == true
    error_message = "The deployment circuit breaker must be enabled."
  }

  assert {
    condition     = aws_ecs_service.app.deployment_circuit_breaker[0].rollback == true
    error_message = "Automatic rollback must be enabled."
  }
}

# 10: Grace period defaults to 120 seconds.
run "grace_period_defaults_to_120_seconds" {
  command = apply

  assert {
    condition     = aws_ecs_service.app.health_check_grace_period_seconds == 120
    error_message = "health_check_grace_period_seconds must default to 120."
  }
}

# 11-12: ECS Service runs in private subnets, no public IP.
run "ecs_service_in_private_subnets_without_public_ip" {
  command = apply

  assert {
    condition     = toset(aws_ecs_service.app.network_configuration[0].subnets) == toset(["subnet-app-a", "subnet-app-b"])
    error_message = "The ECS Service must run in exactly the private application subnets."
  }

  assert {
    condition     = aws_ecs_service.app.network_configuration[0].assign_public_ip == false
    error_message = "The ECS Service must never assign a public IP."
  }
}

# 13: ECS security group only admits ingress from the ALB security group.
run "ecs_ingress_only_from_alb" {
  command = apply

  assert {
    condition     = aws_security_group_rule.ecs_service_ingress_from_alb.source_security_group_id == aws_security_group.alb.id
    error_message = "ECS ingress must come from the ALB security group only."
  }

  assert {
    condition     = aws_security_group_rule.ecs_service_ingress_from_alb.from_port == 8080 && aws_security_group_rule.ecs_service_ingress_from_alb.to_port == 8080
    error_message = "ECS ingress must be scoped to the application port (8080)."
  }
}

# 14 / 17: RDS security group only admits ingress from the ECS security
# group, on port 5432.
run "rds_ingress_only_from_ecs_service_on_5432" {
  command = apply

  assert {
    condition     = aws_security_group_rule.rds_ingress_from_ecs_service.source_security_group_id == aws_security_group.ecs_service.id
    error_message = "RDS ingress must come from the ECS service security group only."
  }

  assert {
    condition     = aws_security_group_rule.rds_ingress_from_ecs_service.security_group_id == "sg-rds-example"
    error_message = "The RDS ingress rule must be attached to the given RDS security group."
  }

  assert {
    condition     = aws_security_group_rule.rds_ingress_from_ecs_service.from_port == 5432 && aws_security_group_rule.rds_ingress_from_ecs_service.to_port == 5432
    error_message = "RDS ingress must be scoped to port 5432."
  }
}

# 15: ALB has no world-open ingress by default (empty allow-lists).
run "alb_has_no_ingress_by_default" {
  command = apply

  assert {
    condition     = length(aws_security_group_rule.alb_ingress_from_security_groups) == 0
    error_message = "No security-group-based ALB ingress rule must exist when alb_allowed_ingress_security_group_ids is empty (the default)."
  }

  assert {
    condition     = length(aws_security_group_rule.alb_ingress_from_cidr_blocks) == 0
    error_message = "No CIDR-based ALB ingress rule must exist when alb_allowed_ingress_cidr_blocks is empty (the default)."
  }
}

# A configured VPC Link security group produces exactly one scoped ingress
# rule — never a CIDR block.
run "alb_ingress_created_for_a_configured_security_group" {
  command = apply

  variables {
    alb_allowed_ingress_security_group_ids = ["sg-vpc-link-example"]
  }

  assert {
    condition     = length(aws_security_group_rule.alb_ingress_from_security_groups) == 1
    error_message = "Exactly one ingress rule must be created per configured security group ID."
  }

  assert {
    condition     = aws_security_group_rule.alb_ingress_from_security_groups[0].source_security_group_id == "sg-vpc-link-example"
    error_message = "The ALB ingress rule must be scoped to the given security group."
  }
}

# Invalid input is rejected: 0.0.0.0/0 in the CIDR fallback.
run "rejects_open_cidr_fallback" {
  command = plan

  variables {
    alb_allowed_ingress_cidr_blocks = ["0.0.0.0/0"]
  }

  expect_failures = [
    var.alb_allowed_ingress_cidr_blocks,
  ]
}

# 16: Container/target group port is 8080.
run "port_matches_the_application" {
  command = apply

  assert {
    condition     = aws_lb_target_group.app.port == 8080
    error_message = "The target group port must match the application's real port (8080)."
  }

  assert {
    condition     = [for lb in aws_ecs_service.app.load_balancer : lb.container_port][0] == 8080
    error_message = "The ECS Service load_balancer block must reference port 8080."
  }
}

# 18: The service reuses the existing task definition ARN — it is not
# rebuilt or redeclared in this module.
run "reuses_existing_task_definition" {
  command = apply

  assert {
    condition     = aws_ecs_service.app.task_definition == "arn:aws:ecs:us-east-1:123456789012:task-definition/sipsa-production-app:1"
    error_message = "The ECS Service must reference the given task definition ARN exactly, not rebuild it."
  }
}

# 20: ECS Exec is disabled by default.
run "ecs_exec_disabled_by_default" {
  command = apply

  assert {
    condition     = aws_ecs_service.app.enable_execute_command == false
    error_message = "enable_execute_command must default to false."
  }
}

# 24: Common tags are applied.
run "common_tags_applied_to_alb_and_service" {
  command = apply

  assert {
    condition     = aws_lb.internal.tags["Project"] == "sipsa" && aws_lb.internal.tags["Environment"] == "production" && aws_lb.internal.tags["Owner"] == "test-owner" && aws_lb.internal.tags["ManagedBy"] == "terraform" && aws_lb.internal.tags["Repository"] == "dalejandrov/sipsa" && aws_lb.internal.tags["CostCenter"] == "test-cost-center"
    error_message = "The ALB must carry every common tag."
  }

  assert {
    condition     = aws_ecs_service.app.tags["Project"] == "sipsa"
    error_message = "The ECS Service must carry the common tags."
  }
}
