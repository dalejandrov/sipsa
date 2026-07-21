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
