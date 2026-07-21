locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

# ---------------------------------------------------------------------------
# ALB security group — no ingress rule by default (TECH-141 scope). TECH-131
# populates alb_allowed_ingress_security_group_ids once API Gateway's VPC
# Link security group exists. Egress is scoped to exactly the ECS service
# security group, on the container port — never a broad egress rule.
# ---------------------------------------------------------------------------

resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb"
  description = "Internal ALB - ingress added per allowed_ingress_security_group_ids/cidr_blocks (TECH-131 populates the VPC Link rule); never 0.0.0.0/0."
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-alb-sg"
  })
}

resource "aws_security_group_rule" "alb_ingress_from_security_groups" {
  count = length(var.alb_allowed_ingress_security_group_ids)

  type                     = "ingress"
  from_port                = 80
  to_port                  = 80
  protocol                 = "tcp"
  source_security_group_id = var.alb_allowed_ingress_security_group_ids[count.index]
  security_group_id        = aws_security_group.alb.id
  description              = "HTTP from a specific consumer security group (e.g. API Gateways VPC Link, TECH-131) - never a CIDR block."
}

resource "aws_security_group_rule" "alb_ingress_from_cidr_blocks" {
  count = length(var.alb_allowed_ingress_cidr_blocks) > 0 ? 1 : 0

  type              = "ingress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = var.alb_allowed_ingress_cidr_blocks
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from a documented, explicitly-approved CIDR fallback (never 0.0.0.0/0 - enforced by variable validation)."
}

resource "aws_security_group_rule" "alb_egress_to_ecs_service" {
  type                     = "egress"
  from_port                = var.container_port
  to_port                  = var.container_port
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ecs_service.id
  security_group_id        = aws_security_group.alb.id
  description              = "ALB reaches the ECS service on the application port only."
}

# ---------------------------------------------------------------------------
# ECS Service security group — ingress only from the ALB, on the container
# port. Egress scoped to exactly what this application needs: PostgreSQL to
# RDS, HTTPS (via NAT) for the DANE SOAP endpoint and AWS service traffic,
# and DNS. No "all traffic" egress rule.
# ---------------------------------------------------------------------------

resource "aws_security_group" "ecs_service" {
  name        = "${local.name_prefix}-ecs-service"
  description = "ECS service - ingress only from the ALB security group on the application port."
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-ecs-service-sg"
  })
}

resource "aws_security_group_rule" "ecs_service_ingress_from_alb" {
  type                     = "ingress"
  from_port                = var.container_port
  to_port                  = var.container_port
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb.id
  security_group_id        = aws_security_group.ecs_service.id
  description              = "Application traffic from the ALB only."
}

resource "aws_security_group_rule" "ecs_service_egress_to_rds" {
  type      = "egress"
  from_port = var.rds_port
  to_port   = var.rds_port
  protocol  = "tcp"
  # source_security_group_id doubles as the destination reference for an
  # egress rule in this resource type — the field name is the same
  # regardless of direction. The RDS-side counterpart (the ingress half of
  # this same relationship) is the rds_ingress_from_ecs_service rule below.
  source_security_group_id = var.rds_security_group_id
  security_group_id        = aws_security_group.ecs_service.id
  description              = "PostgreSQL to RDS - the RDS security group only, never a CIDR block."
}

# Trivy flags cidr_blocks = 0.0.0.0/0 on an egress rule as unrestricted
# (AWS-0104). Exception, not an oversight: this is a single port (443),
# egress-only rule with no matching ingress from the internet anywhere in
# this security group, and 0.0.0.0/0 is unavoidable here specifically
# because the destination — DANE's SOAP endpoint (SOAP_ENDPOINT) — is a
# public internet URL this application must reach outbound, not an AWS
# resource with a fixed IP range to scope to (the same fact that already
# justifies TECH-138's NAT Gateway existing at all). AWS service APIs
# (Secrets Manager, CloudWatch Logs, ECR) are reached the same way. No
# narrower destination is technically expressible for a public third-party
# endpoint outside AWS.
# trivy:ignore:AVD-AWS-0104
resource "aws_security_group_rule" "ecs_service_egress_https" {
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.ecs_service.id
  description       = "HTTPS via the NAT Gateway - public DANE SOAP endpoint and AWS service APIs. Egress-only, not an inbound exposure."
}

resource "aws_security_group_rule" "ecs_service_egress_dns_tcp" {
  type              = "egress"
  from_port         = 53
  to_port           = 53
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.selected.cidr_block]
  security_group_id = aws_security_group.ecs_service.id
  description       = "DNS (TCP) to the VPC own resolver range only."
}

resource "aws_security_group_rule" "ecs_service_egress_dns_udp" {
  type              = "egress"
  from_port         = 53
  to_port           = 53
  protocol          = "udp"
  cidr_blocks       = [data.aws_vpc.selected.cidr_block]
  security_group_id = aws_security_group.ecs_service.id
  description       = "DNS (UDP) to the VPC own resolver range only."
}

data "aws_vpc" "selected" {
  id = var.vpc_id
}

# ---------------------------------------------------------------------------
# ECS -> RDS ingress rule. modules/database creates the RDS security group
# with NO ingress rule of its own (see its README) — this is the rule that
# actually grants access, added here because this is the first module that
# has both security groups available to reference each other.
# ---------------------------------------------------------------------------

resource "aws_security_group_rule" "rds_ingress_from_ecs_service" {
  type                     = "ingress"
  from_port                = var.rds_port
  to_port                  = var.rds_port
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ecs_service.id
  security_group_id        = var.rds_security_group_id
  description              = "PostgreSQL from the ECS service security group only - never a CIDR block."
}

# ---------------------------------------------------------------------------
# Internal ALB — never internet-facing. Placed in the private-application
# subnet tier (not a dedicated ALB tier): no other workload exists in that
# tier today, so a separate subnet/route-table tier would add topology
# complexity without a corresponding isolation benefit. Revisit only if a
# real requirement for ALB/compute network separation emerges.
# ---------------------------------------------------------------------------

resource "aws_lb" "internal" {
  name               = "${local.name_prefix}-internal-alb"
  internal           = true
  load_balancer_type = "application"
  subnets            = var.private_app_subnet_ids
  security_groups    = [aws_security_group.alb.id]

  enable_deletion_protection = var.alb_deletion_protection
  drop_invalid_header_fields = true
  desync_mitigation_mode     = var.desync_mitigation_mode
  enable_http2               = true

  dynamic "access_logs" {
    for_each = var.enable_alb_access_logs ? [1] : []

    content {
      enabled = true
      bucket  = var.alb_access_logs_bucket
      prefix  = var.alb_access_logs_prefix
    }
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-internal-alb"
  })
}

# ---------------------------------------------------------------------------
# Target group — IP target type (required for awsvpc-networked Fargate
# tasks). Health check uses the application's own real, already-safe
# unauthenticated endpoint (see health_check_path's variable description).
# ---------------------------------------------------------------------------

resource "aws_lb_target_group" "app" {
  name        = "${local.name_prefix}-app"
  target_type = "ip"
  protocol    = "HTTP"
  port        = var.container_port
  vpc_id      = var.vpc_id

  health_check {
    path                = var.health_check_path
    matcher             = var.health_check_matcher
    protocol            = "HTTP"
    port                = "traffic-port"
    interval            = var.health_check_interval_seconds
    timeout             = var.health_check_timeout_seconds
    healthy_threshold   = var.health_check_healthy_threshold
    unhealthy_threshold = var.health_check_unhealthy_threshold
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-app-tg"
  })
}

# ---------------------------------------------------------------------------
# Listener — HTTP only. No domain and no ACM certificate exist yet
# (ADR-010) — HTTPS-with-a-nonexistent-certificate is not simulated. This
# is acceptable specifically because the ALB is internal-only, reachable
# solely within the VPC; API Gateway (TECH-131) is, and will remain, the
# only public entry point. If internal encryption-in-transit is ever
# decided as a requirement (VPC-internal TLS policy remains an open
# question — aws-production-readiness.md), that is a deliberate future
# decision, not assumed here.
#
# Trivy flags this as a plaintext listener (AWS-0054, normally a red flag
# for a public-facing ALB). Exception, not a gap: this ALB's own security
# group has zero ingress from the internet (see aws_security_group.alb
# above and its own tests) and internal = true means AWS never assigns it
# a public DNS/IP resolvable outside the VPC either — HTTPS would encrypt
# traffic that never leaves a private network boundary in the first place.
# Revisit only if VPC-internal TLS becomes a real compliance requirement,
# together with a real certificate strategy for that scenario — not
# simulated preemptively with no certificate to back it.
# ---------------------------------------------------------------------------
# trivy:ignore:AVD-AWS-0054
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.internal.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-http-listener"
  })
}

# ---------------------------------------------------------------------------
# ECS Service. Consumes the existing task definition (modules/ecs-task) by
# ARN — never rebuilds or redeclares it here, keeping the two modules'
# responsibilities separate. No image has been published to ECR yet; this
# resource is declarative infrastructure only, never actually applied by
# this story (no terraform apply is run).
# ---------------------------------------------------------------------------

resource "aws_ecs_service" "app" {
  name            = "${local.name_prefix}-app"
  cluster         = var.ecs_cluster_id
  task_definition = var.task_definition_arn
  launch_type     = "FARGATE"
  desired_count   = var.desired_count

  enable_execute_command = var.enable_execute_command

  network_configuration {
    subnets          = var.private_app_subnet_ids
    security_groups  = [aws_security_group.ecs_service.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.container_name
    container_port   = var.container_port
  }

  deployment_minimum_healthy_percent = var.deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.deployment_maximum_percent

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  health_check_grace_period_seconds = var.health_check_grace_period_seconds

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-app-service"
  })

  # The service must not attach to the target group before the listener
  # exists to forward traffic to it.
  depends_on = [aws_lb_listener.http]
}
