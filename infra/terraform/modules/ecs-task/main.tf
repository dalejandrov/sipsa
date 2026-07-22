locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

# ---------------------------------------------------------------------------
# ECS Cluster — Fargate only, no EC2 capacity provider. FARGATE_SPOT is
# deliberately not used for this task: the single production task runs
# scheduled ingestion jobs against a public SOAP endpoint with retry logic
# tuned for a live run, not for being interrupted mid-ingestion by Spot
# reclamation — an interruption during a daily/monthly ingestion window
# risks a partial, hard-to-diagnose ingestion run.
# ---------------------------------------------------------------------------

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-cluster"
  })
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name

  # FARGATE only — no FARGATE_SPOT, no EC2 capacity provider. See the
  # cluster resource's comment above for why Spot is excluded for this
  # specific workload.
  capacity_providers = ["FARGATE"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

# ---------------------------------------------------------------------------
# Application log group — created ahead of the task definition so retention
# is set from the start.
#
# AWS-owned key (CloudWatch Logs' own default encryption), not a
# customer-managed KMS key — the same call already made for the RDS module's
# log groups and the network module's Flow Logs group; no compliance
# requirement for a dedicated key has been identified. Revisit if that
# changes.
# ---------------------------------------------------------------------------
# trivy:ignore:AVD-AWS-0017
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name_prefix}"
  retention_in_days = var.log_retention_days

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-app-logs"
  })
}

# ---------------------------------------------------------------------------
# Execution role — used by the ECS agent to prepare the task: pull the
# image from ECR, write to CloudWatch Logs, and resolve the `secrets`
# entries referenced in the container definition below. NOT the same as the
# task role (next section) — the application itself never assumes this
# role.
# ---------------------------------------------------------------------------

locals {
  ecs_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role" "execution" {
  name               = "${local.name_prefix}-ecs-execution"
  assume_role_policy = local.ecs_assume_role_policy

  tags = var.common_tags
}

# The standard AWS-managed policy for ECR pull + CloudWatch Logs write —
# exactly the scope the execution role needs for those two responsibilities,
# nothing broader (not AdministratorAccess, not PowerUserAccess).
resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Secrets Manager / SSM Parameter Store read access — scoped to the exact
# ARNs the container definition's `secrets` entries reference, never
# Resource = "*" and never the broad AWS-managed SecretsManagerReadWrite.
resource "aws_iam_role_policy" "execution_secrets" {
  name = "${local.name_prefix}-ecs-execution-secrets"
  role = aws_iam_role.execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [
        {
          Effect   = "Allow"
          Action   = ["secretsmanager:GetSecretValue"]
          Resource = concat([var.db_credentials_secret_arn], var.execution_extra_secret_arns)
        }
      ],
      length(var.execution_ssm_parameter_arns) > 0 ? [
        {
          Effect   = "Allow"
          Action   = ["ssm:GetParameters"]
          Resource = var.execution_ssm_parameter_arns
        }
      ] : []
    )
  })
}

# ---------------------------------------------------------------------------
# Task role — the credentials available to the APPLICATION at runtime,
# entirely separate from the execution role above. Empty by default
# (task_role_policy_arns): the application does not call any AWS API
# directly today (Spring Data JPA against RDS via a database credential,
# not IAM) — no RDS IAM auth, no permission the application doesn't already
# need. Populate task_role_policy_arns only once a real AWS API need is
# confirmed.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "task" {
  name               = "${local.name_prefix}-ecs-task"
  assume_role_policy = local.ecs_assume_role_policy

  tags = var.common_tags
}

resource "aws_iam_role_policy_attachment" "task_extra" {
  count = length(var.task_role_policy_arns)

  role       = aws_iam_role.task.name
  policy_arn = var.task_role_policy_arns[count.index]
}

# ---------------------------------------------------------------------------
# Task definition — Fargate, awsvpc networking. No ECS Service, no ALB, no
# target group exists yet (TECH-140's explicit scope boundary) — this
# defines what a future task WOULD run, not a running task.
# ---------------------------------------------------------------------------

resource "aws_ecs_task_definition" "app" {
  family                   = "${local.name_prefix}-app"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.cpu)
  memory                   = tostring(var.memory)
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = var.cpu_architecture
    operating_system_family = "LINUX"
  }

  # No ephemeral_storage block: Fargate's default (20 GiB) is left as-is —
  # this workload's only disk use is JVM/library temp files under /tmp (see
  # the tmpfs mount below), SOAP response buffering, and non-persistent
  # logs (stdout, captured by awslogs) — no evidence of a need for more.
  # Raising this without evidence would be paying for unused storage.

  container_definitions = jsonencode([
    {
      name      = var.container_name
      image     = "${var.ecr_repository_url}:${var.image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]

      # environment_variables/secret_parameters (both maps, TECH-142) let the
      # caller append entries without this module knowing which other module
      # produced the value — see their variable descriptions. `for k, v in`
      # over a map iterates in a stable, sorted-by-key order, so this list's
      # shape is deterministic across plans/tests.
      environment = concat([
        { name = "SPRING_PROFILES_ACTIVE", value = var.spring_profile },
        { name = "PORT", value = tostring(var.container_port) },
        { name = "DB_HOST", value = var.db_host },
        { name = "DB_PORT", value = tostring(var.db_port) },
        { name = "DB_NAME", value = var.db_name },
        ], [for k, v in var.environment_variables : { name = k, value = v }]
      )

      # DB_USERNAME/DB_PASSWORD resolved from Secrets Manager at task
      # startup — never a plaintext environment value. See
      # db_credentials_secret_arn's variable description for why this is a
      # temporary wiring (the RDS master secret) pending a real,
      # minimum-privilege application credential.
      secrets = concat([
        { name = "DB_USERNAME", valueFrom = "${var.db_credentials_secret_arn}:username::" },
        { name = "DB_PASSWORD", valueFrom = "${var.db_credentials_secret_arn}:password::" },
        ], [for k, v in var.secret_parameters : { name = k, valueFrom = v }]
      )

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = var.container_name
        }
      }

      readonlyRootFilesystem = true

      # No evidence the application writes to disk anywhere (confirmed by
      # inspection: no java.io.File/Files.write/createTempFile usage in
      # src/main/java) — but the JVM and its libraries (JAXB/CXF schema
      # handling, reflection scratch space, etc.) may still want /tmp, so a
      # small tmpfs mount is provided there rather than risking an
      # unvalidated fully-read-only filesystem breaking the app on day one.
      linuxParameters = {
        tmpfs = [
          {
            containerPath = "/tmp"
            size          = 128
          }
        ]
      }
    }
  ])

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-app-task"
  })
}
