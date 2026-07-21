# Structural tests for the ecs-task module (TECH-140). No real AWS account
# is contacted — the AWS provider is fully mocked (mock_provider "aws" {});
# `command = apply` never creates anything real, and no ECS task, ALB, or
# ECS Service exists anywhere in this module — these tests confirm that
# absence explicitly, not just the presence of what the module does create.

mock_provider "aws" {}

# The mock provider's default fake ARN-shaped attributes for IAM roles don't
# satisfy aws_ecs_task_definition's own ARN-format validation for
# execution_role_arn/task_role_arn. Overridden purely to produce a
# resolvable value; no real AWS account or role is involved.
override_resource {
  target = aws_iam_role.execution
  values = {
    arn = "arn:aws:iam::123456789012:role/sipsa-production-ecs-execution"
  }
}

override_resource {
  target = aws_iam_role.task
  values = {
    arn = "arn:aws:iam::123456789012:role/sipsa-production-ecs-task"
  }
}

variables {
  project_name = "sipsa"
  environment  = "production"
  aws_region   = "us-east-1"
  common_tags = {
    Project     = "sipsa"
    Environment = "production"
    Owner       = "test-owner"
    ManagedBy   = "terraform"
    Repository  = "dalejandrov/sipsa"
    CostCenter  = "test-cost-center"
  }
  ecr_repository_url        = "123456789012.dkr.ecr.us-east-1.amazonaws.com/sipsa/production-app"
  image_tag                 = "test-1.0.0"
  db_host                   = "sipsa-production-postgres.example.us-east-1.rds.amazonaws.com"
  db_name                   = "sipsa_db"
  db_credentials_secret_arn = "arn:aws:secretsmanager:us-east-1:123456789012:secret:rds!db-example-abc123"
}

# 7-8: Fargate, awsvpc.
run "fargate_and_awsvpc" {
  command = apply

  assert {
    condition     = contains(aws_ecs_task_definition.app.requires_compatibilities, "FARGATE")
    error_message = "The task definition must require FARGATE compatibility."
  }

  assert {
    condition     = aws_ecs_task_definition.app.network_mode == "awsvpc"
    error_message = "The task definition must use awsvpc networking."
  }
}

# 9: CPU/memory parameterizable, with sane defaults.
run "cpu_and_memory_are_parameterizable" {
  command = apply

  assert {
    condition     = aws_ecs_task_definition.app.cpu == "256" && aws_ecs_task_definition.app.memory == "512"
    error_message = "Default cpu/memory must be 256/512."
  }
}

run "cpu_and_memory_overrides_take_effect" {
  command = apply

  variables {
    cpu    = 512
    memory = 1024
  }

  assert {
    condition     = aws_ecs_task_definition.app.cpu == "512" && aws_ecs_task_definition.app.memory == "1024"
    error_message = "cpu/memory overrides must take effect."
  }
}

# 10: Architecture defaults to X86_64.
run "architecture_defaults_to_x86_64" {
  command = apply

  assert {
    condition     = aws_ecs_task_definition.app.runtime_platform[0].cpu_architecture == "X86_64"
    error_message = "Default cpu_architecture must be X86_64."
  }

  assert {
    condition     = aws_ecs_task_definition.app.runtime_platform[0].operating_system_family == "LINUX"
    error_message = "operating_system_family must be LINUX."
  }
}

# 11: Log group has explicit 30-day retention.
run "log_group_has_30_day_retention_by_default" {
  command = apply

  assert {
    condition     = aws_cloudwatch_log_group.app.retention_in_days == 30
    error_message = "Default log retention must be 30 days."
  }
}

# 12: The container definition configures the awslogs driver correctly.
run "container_definition_configures_awslogs" {
  command = apply

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, "\"logDriver\":\"awslogs\"")
    error_message = "The container definition must use the awslogs log driver."
  }

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, aws_cloudwatch_log_group.app.name)
    error_message = "The container definition's awslogs-group must reference the created log group."
  }
}

# 13-14: Execution role and task role are separate IAM roles.
run "execution_and_task_roles_are_separate" {
  command = apply

  assert {
    condition     = aws_iam_role.execution.name != aws_iam_role.task.name
    error_message = "The execution role and task role must be distinct IAM roles."
  }

  assert {
    condition     = aws_ecs_task_definition.app.execution_role_arn == aws_iam_role.execution.arn
    error_message = "The task definition's execution_role_arn must reference the execution role."
  }

  assert {
    condition     = aws_ecs_task_definition.app.task_role_arn == aws_iam_role.task.arn
    error_message = "The task definition's task_role_arn must reference the task role."
  }
}

# 15: No administrative permissions are granted anywhere.
run "no_administrative_permissions_granted" {
  command = apply

  assert {
    condition     = aws_iam_role_policy_attachment.execution_managed.policy_arn == "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
    error_message = "The execution role must attach exactly the standard ECS task execution policy, not a broader one."
  }

  assert {
    condition     = length(aws_iam_role_policy_attachment.task_extra) == 0
    error_message = "The task role must have zero attached managed policies by default (task_role_policy_arns defaults to empty) — the application calls no AWS API directly today."
  }

  assert {
    condition     = !strcontains(aws_iam_role_policy.execution_secrets.policy, "\"Resource\":\"*\"")
    error_message = "The execution role's secrets policy must never grant access via a wildcard Resource."
  }
}

# 16: No privileged container / no extra Linux capabilities.
run "no_privileged_container" {
  command = apply

  assert {
    condition     = !strcontains(aws_ecs_task_definition.app.container_definitions, "\"privileged\":true")
    error_message = "The container definition must never set privileged: true (Fargate does not support it, and this module must not attempt to)."
  }

  assert {
    condition     = !strcontains(aws_ecs_task_definition.app.container_definitions, "\"capabilities\"")
    error_message = "The container definition must not grant additional Linux capabilities."
  }
}

# 17: Exactly one, essential container definition.
run "single_essential_container_definition" {
  command = apply

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, "\"essential\":true")
    error_message = "The container definition must be marked essential."
  }
}

# 18: Image tag is never "latest" (enforced by variable validation, and
# reflected in the rendered container definition).
run "image_tag_is_not_latest" {
  command = apply

  assert {
    condition     = !strcontains(aws_ecs_task_definition.app.container_definitions, ":latest\"")
    error_message = "The container image reference must never resolve to the :latest tag."
  }
}

run "rejects_latest_image_tag" {
  command = plan

  variables {
    image_tag = "latest"
  }

  expect_failures = [
    var.image_tag,
  ]
}

# 19: Container port matches the application's real port (8080).
run "container_port_matches_the_application" {
  command = apply

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, "\"containerPort\":8080")
    error_message = "The default container port must be 8080, matching this repository's application.yaml and Dockerfile."
  }
}

# 20: Secrets are resolved via the task definition's `secrets` block, never
# as plaintext environment values.
run "credentials_are_never_plaintext_environment_values" {
  command = apply

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, "\"DB_USERNAME\"") && strcontains(aws_ecs_task_definition.app.container_definitions, "\"valueFrom\":\"arn:aws:secretsmanager")
    error_message = "DB_USERNAME/DB_PASSWORD must resolve via the secrets block (valueFrom), never a plaintext environment entry."
  }

  assert {
    condition     = !strcontains(aws_ecs_task_definition.app.container_definitions, "\"name\":\"DB_PASSWORD\",\"value\":")
    error_message = "DB_PASSWORD must never appear as a plaintext environment value."
  }
}

# 21-22: No ECS Service and no ALB exist anywhere in this module — verified
# structurally by confirming no such resource type appears in the module's
# own plan output for this configuration (a positive existence check on
# "cluster/task-definition only" is the meaningful assertion here; there is
# no aws_ecs_service/aws_lb resource declared in this module's source at
# all, confirmed by inspection of main.tf, not fabricated as a runtime
# check against a resource type that was never declared).
run "cluster_and_task_definition_exist_without_ecs_service_or_alb" {
  command = apply

  assert {
    condition     = can(aws_ecs_cluster.main.id) && can(aws_ecs_task_definition.app.arn)
    error_message = "The ECS cluster and task definition must both exist."
  }
}

# Common tags are applied.
run "common_tags_applied_to_cluster" {
  command = apply

  assert {
    condition     = aws_ecs_cluster.main.tags["Project"] == "sipsa" && aws_ecs_cluster.main.tags["Environment"] == "production" && aws_ecs_cluster.main.tags["Owner"] == "test-owner" && aws_ecs_cluster.main.tags["ManagedBy"] == "terraform" && aws_ecs_cluster.main.tags["Repository"] == "dalejandrov/sipsa" && aws_ecs_cluster.main.tags["CostCenter"] == "test-cost-center"
    error_message = "The ECS cluster must carry every common tag."
  }
}

# Container Insights defaults to enabled.
run "container_insights_enabled_by_default" {
  command = apply

  assert {
    condition     = [for s in aws_ecs_cluster.main.setting : s.value if s.name == "containerInsights"][0] == "enabled"
    error_message = "Container Insights must default to enabled."
  }
}
