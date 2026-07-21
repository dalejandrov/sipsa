provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

locals {
  # Every module in this stack (network, cognito, database, ecs, api-gateway,
  # observability — added one real story at a time, per ADR-010's Fase 1-5
  # sequence) must apply these tags. Centralized here so a tag change only
  # happens in one place.
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    Owner       = var.owner
    ManagedBy   = var.managed_by
    Repository  = var.repository
    CostCenter  = var.cost_center
  }
}

# TECH-138 (ADR-010 Fase 1, partial TECH-132): VPC, subnets, routing, NAT,
# S3 Gateway Endpoint, VPC Flow Logs. No compute, database, identity, or
# gateway resource yet — those are added by their own stories against this
# module's outputs.
module "network" {
  source = "../../modules/network"

  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region
  common_tags  = local.common_tags

  vpc_cidr                      = var.vpc_cidr
  public_subnet_cidrs           = var.public_subnet_cidrs
  private_app_subnet_cidrs      = var.private_app_subnet_cidrs
  private_database_subnet_cidrs = var.private_database_subnet_cidrs

  enable_vpc_flow_logs    = var.enable_vpc_flow_logs
  flow_log_traffic_type   = var.flow_log_traffic_type
  flow_log_retention_days = var.flow_log_retention_days
}

# TECH-139 (ADR-010, partial TECH-132): DB subnet group, RDS security group
# (no ingress rule yet), parameter group, and the RDS PostgreSQL instance.
# No ECS/ALB exists yet to grant access to — allowed_security_group_ids stays
# empty until TECH-132's compute phase passes its ECS security group ID.
module "database" {
  source = "../../modules/database"

  project_name = var.project_name
  environment  = var.environment
  common_tags  = local.common_tags

  vpc_id                      = module.network.vpc_id
  private_database_subnet_ids = module.network.private_database_subnet_ids

  postgres_engine_version    = var.postgres_engine_version
  auto_minor_version_upgrade = var.db_auto_minor_version_upgrade

  instance_class        = var.db_instance_class
  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = var.db_storage_type

  database_name   = var.db_database_name
  master_username = var.db_master_username
  port            = var.db_port

  backup_retention_period  = var.db_backup_retention_period
  backup_window            = var.db_backup_window
  maintenance_window       = var.db_maintenance_window
  copy_tags_to_snapshot    = var.db_copy_tags_to_snapshot
  delete_automated_backups = var.db_delete_automated_backups
  deletion_protection      = var.db_deletion_protection
  skip_final_snapshot      = var.db_skip_final_snapshot

  multi_az = var.db_multi_az

  performance_insights_enabled    = var.db_performance_insights_enabled
  monitoring_interval             = var.db_monitoring_interval
  enabled_cloudwatch_logs_exports = var.db_enabled_cloudwatch_logs_exports
  log_retention_days              = var.db_log_retention_days
}
