locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # Parameter group family is derived from the major version, e.g.
  # postgres_engine_version = "18" or "18.1" both yield "postgres18".
  postgres_major_version = split(".", var.postgres_engine_version)[0]
  parameter_group_family = "postgres${local.postgres_major_version}"
}

# ---------------------------------------------------------------------------
# DB subnet group — private database subnets only (TECH-138's network module)
# ---------------------------------------------------------------------------

resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-postgres"
  subnet_ids = var.private_database_subnet_ids

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-postgres-subnet-group"
  })
}

# ---------------------------------------------------------------------------
# Security group — no ingress rule created here (TECH-139 scope). A future
# story (TECH-132's compute phase) passes its ECS security group ID via
# allowed_security_group_ids, which creates exactly one ingress rule per ID.
# Default egress (all outbound) is left at the AWS API default — RDS is a
# managed service, not an EC2 instance the customer administers, and no
# specific requirement to restrict its egress has been identified.
# ---------------------------------------------------------------------------

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-postgres"
  description = "RDS PostgreSQL access (ingress added per-consumer via allowed_security_group_ids, never a CIDR rule)."
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-postgres-sg"
  })
}

resource "aws_security_group_rule" "rds_ingress" {
  count = length(var.allowed_security_group_ids)

  type                     = "ingress"
  from_port                = var.port
  to_port                  = var.port
  protocol                 = "tcp"
  source_security_group_id = var.allowed_security_group_ids[count.index]
  security_group_id        = aws_security_group.rds.id
  description              = "PostgreSQL access from a specific consumer security group (never a CIDR block)."
}

# ---------------------------------------------------------------------------
# Parameter group — only the parameters this story has real evidence for.
# rds.force_ssl is deliberately NOT set: this application's JDBC URL
# (application.yaml) does not specify an sslmode today, and forcing SSL at
# the database before confirming the JDBC client negotiates it correctly
# would risk breaking the future production connection outright. Documented
# gap, not silently resolved either way — see README.md.
# ---------------------------------------------------------------------------

resource "aws_db_parameter_group" "main" {
  name   = "${local.name_prefix}-postgres"
  family = local.parameter_group_family

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-postgres-params"
  })
}

# ---------------------------------------------------------------------------
# CloudWatch log groups — created ahead of the RDS instance so retention is
# set from the start; RDS's own auto-created log groups default to never
# expire otherwise.
# ---------------------------------------------------------------------------

# AWS-owned key (CloudWatch Logs' own default encryption), not a
# customer-managed KMS key: this group holds the PostgreSQL server log
# (connections/disconnections per the parameter group above), not query
# content or application data. A dedicated KMS key/policy for it is
# complexity this stage doesn't need — the same call already made for the
# Terraform state bucket (bootstrap/main.tf) and the network module's Flow
# Logs group. Revisit if a compliance requirement makes this necessary.
# trivy:ignore:AVD-AWS-0017
resource "aws_cloudwatch_log_group" "postgresql" {
  count = contains(var.enabled_cloudwatch_logs_exports, "postgresql") ? 1 : 0

  name              = "/aws/rds/instance/${local.name_prefix}-postgres/postgresql"
  retention_in_days = var.log_retention_days

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-postgres-logs"
  })
}

# Same AWS-owned-key rationale as the postgresql log group above — this one
# holds only major/minor engine upgrade history, lower sensitivity still.
# trivy:ignore:AVD-AWS-0017
resource "aws_cloudwatch_log_group" "upgrade" {
  count = contains(var.enabled_cloudwatch_logs_exports, "upgrade") ? 1 : 0

  name              = "/aws/rds/instance/${local.name_prefix}-postgres/upgrade"
  retention_in_days = var.log_retention_days

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-postgres-upgrade-logs"
  })
}

# ---------------------------------------------------------------------------
# Final snapshot identifier — AWS requires this to be unique per account/
# region; a static name would fail on a second destroy that reuses it.
# random_id is stable across repeated plans of the same resource instance
# (only changes if the resource itself is replaced), giving a reproducible-
# per-instance but always-unique-across-recreations identifier.
# ---------------------------------------------------------------------------

resource "random_id" "final_snapshot_suffix" {
  byte_length = 4
}

# ---------------------------------------------------------------------------
# RDS instance
#
# performance_insights_enabled defaults to false (ADR-010/TECH-139): an
# added cost with no established need yet at this stage — see README.md
# "Monitoring" for what's lost by leaving it disabled and when to enable it.
#
# IAM Database Authentication is not enabled: it requires application-side
# changes (generating short-lived IAM auth tokens instead of using the
# RDS-managed Secrets Manager password) that are out of TECH-139's scope —
# this story creates no ECS task and makes no real connection to this
# database at all. The current design (manage_master_user_password = true,
# a real AWS-managed secret, no plaintext credential anywhere) is already a
# strong baseline; IAM auth is a complementary hardening option to evaluate
# once TECH-132's ECS task exists and a real connection path is being
# designed, not before.
# ---------------------------------------------------------------------------
# trivy:ignore:AVD-AWS-0133
# trivy:ignore:AVD-AWS-0176
resource "aws_db_instance" "main" {
  identifier = "${local.name_prefix}-postgres"

  engine         = "postgres"
  engine_version = var.postgres_engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = var.storage_type
  storage_encrypted     = true

  db_name  = var.database_name
  username = var.master_username
  port     = var.port

  # RDS creates and owns the master password in Secrets Manager directly —
  # Terraform never sees, stores, or manages the password value itself, only
  # a reference to the secret's ARN (see outputs.tf).
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.main.name
  publicly_accessible    = false

  multi_az = var.multi_az

  backup_retention_period  = var.backup_retention_period
  backup_window            = var.backup_window
  maintenance_window       = var.maintenance_window
  copy_tags_to_snapshot    = var.copy_tags_to_snapshot
  delete_automated_backups = var.delete_automated_backups

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${local.name_prefix}-postgres-final-${random_id.final_snapshot_suffix.hex}"

  auto_minor_version_upgrade = var.auto_minor_version_upgrade

  performance_insights_enabled = var.performance_insights_enabled
  monitoring_interval          = var.monitoring_interval

  enabled_cloudwatch_logs_exports = var.enabled_cloudwatch_logs_exports

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-postgres"
  })

  depends_on = [
    aws_cloudwatch_log_group.postgresql,
    aws_cloudwatch_log_group.upgrade,
  ]
}
