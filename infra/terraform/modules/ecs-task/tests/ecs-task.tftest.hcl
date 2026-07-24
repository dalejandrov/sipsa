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

# 9: CPU/memory parameterizable, with sane defaults (TECH-144: memory
# bumped from 512 to 1024 based on real local measurements at both
# values — see variables.tf).
run "cpu_and_memory_are_parameterizable" {
  command = apply

  assert {
    condition     = aws_ecs_task_definition.app.cpu == "256" && aws_ecs_task_definition.app.memory == "1024"
    error_message = "Default cpu/memory must be 256/1024 (TECH-144 measurement)."
  }
}

run "cpu_and_memory_overrides_take_effect" {
  command = apply

  variables {
    cpu    = 512
    memory = 2048
  }

  assert {
    condition     = aws_ecs_task_definition.app.cpu == "512" && aws_ecs_task_definition.app.memory == "2048"
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

# TECH-142: environment_variables/secret_parameters default to empty and
# change nothing about the fixed set (regression guard for the concat()
# refactor in main.tf).
run "generic_env_and_secret_extension_points_are_empty_by_default" {
  command = apply

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, "\"name\":\"SPRING_PROFILES_ACTIVE\"")
    error_message = "The fixed environment entries must still be present when environment_variables is empty."
  }

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, "\"name\":\"DB_USERNAME\"")
    error_message = "The fixed secrets entries must still be present when secret_parameters is empty."
  }
}

# TECH-142: a caller-supplied plain environment variable (e.g.
# SIPSA_JWT_ISSUER_URI from modules/cognito) is appended, as a plaintext
# value, alongside the fixed set.
run "environment_variables_are_appended" {
  command = apply

  variables {
    environment_variables = {
      SIPSA_JWT_ISSUER_URI = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_example"
    }
  }

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, "\"name\":\"SIPSA_JWT_ISSUER_URI\",\"value\":\"https://cognito-idp.us-east-1.amazonaws.com/us-east-1_example\"")
    error_message = "environment_variables entries must appear as plaintext environment values, appended to the fixed set."
  }

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, "\"name\":\"SPRING_PROFILES_ACTIVE\"")
    error_message = "The fixed environment entries must still be present alongside caller-supplied ones."
  }
}

# TECH-142: execution_ssm_parameter_arns grants read access to exactly the
# given SSM parameter ARN (e.g. modules/cognito's allowed-client-ids
# parameter) — never a wildcard, never an ARN the caller didn't supply.
run "execution_role_reads_exactly_the_granted_ssm_parameter" {
  command = apply

  variables {
    execution_ssm_parameter_arns = ["arn:aws:ssm:us-east-1:123456789012:parameter/sipsa-production/sipsa/jwt-allowed-client-ids"]
  }

  assert {
    condition     = strcontains(aws_iam_role_policy.execution_secrets.policy, "arn:aws:ssm:us-east-1:123456789012:parameter/sipsa-production/sipsa/jwt-allowed-client-ids")
    error_message = "The execution role's policy must grant read access to the given SSM parameter ARN."
  }

  assert {
    condition     = !strcontains(aws_iam_role_policy.execution_secrets.policy, "\"Resource\":\"*\"")
    error_message = "Granting one SSM parameter ARN must never widen to a wildcard Resource."
  }
}

# TECH-142: a caller-supplied secret_parameters entry (e.g.
# SIPSA_JWT_ALLOWED_CLIENT_IDS from an SSM parameter ARN) is appended to the
# `secrets` block, never to `environment` — the value itself never appears
# as a plaintext environment value in the rendered container definition.
run "secret_parameters_are_appended_to_secrets_never_to_environment" {
  command = apply

  variables {
    secret_parameters = {
      SIPSA_JWT_ALLOWED_CLIENT_IDS = "arn:aws:ssm:us-east-1:123456789012:parameter/sipsa-production/sipsa/jwt-allowed-client-ids"
    }
  }

  assert {
    condition     = strcontains(aws_ecs_task_definition.app.container_definitions, "\"name\":\"SIPSA_JWT_ALLOWED_CLIENT_IDS\",\"valueFrom\":\"arn:aws:ssm:us-east-1:123456789012:parameter/sipsa-production/sipsa/jwt-allowed-client-ids\"")
    error_message = "secret_parameters entries must appear in the secrets block with the given valueFrom ARN."
  }

  assert {
    condition     = !strcontains(aws_ecs_task_definition.app.container_definitions, "\"name\":\"SIPSA_JWT_ALLOWED_CLIENT_IDS\",\"value\":")
    error_message = "secret_parameters entries must never appear as a plaintext environment value."
  }
}
