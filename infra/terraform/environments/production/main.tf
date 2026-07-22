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

# TECH-140 (ADR-010, partial TECH-132): ECR repository for the application
# image. No image is pushed by this story.
module "ecr" {
  source = "../../modules/ecr"

  project_name = var.project_name
  environment  = var.environment
  common_tags  = local.common_tags

  encryption_type            = var.ecr_encryption_type
  kms_key_id                 = var.ecr_kms_key_id
  keep_last_tagged_images    = var.ecr_keep_last_tagged_images
  expire_untagged_after_days = var.ecr_expire_untagged_after_days
}

# TECH-140 (ADR-010, partial TECH-132): ECS cluster and Fargate task
# definition. No ECS Service, no ALB exists yet — this defines what a
# future task would run, not a running task.
#
# db_credentials_secret_arn is wired to the RDS master secret as an
# explicitly TEMPORARY placeholder (see modules/ecs-task/README.md) — not
# the final design. A real deployment must replace this with a dedicated,
# minimum-privilege application database credential first.
#
# TECH-142: SIPSA_JWT_ISSUER_URI/SIPSA_JWT_ALLOWED_CLIENT_IDS are wired here
# from module.cognito's outputs, via ecs-task's generic environment_variables/
# secret_parameters variables — modules/ecs-task itself has no direct
# dependency on modules/cognito (see both modules' README.md). The allowlist
# is guarded with compact()/a conditional map entry because
# allowed_client_ids_parameter_arn is null when
# var.cognito_publish_client_ids_to_ssm is false (default true).
module "ecs_task" {
  source = "../../modules/ecs-task"

  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region
  common_tags  = local.common_tags

  enable_container_insights = var.ecs_enable_container_insights

  ecr_repository_url = module.ecr.repository_url
  image_tag          = var.ecs_image_tag

  container_name = var.ecs_container_name
  container_port = var.ecs_container_port

  cpu              = var.ecs_task_cpu
  memory           = var.ecs_task_memory
  cpu_architecture = var.ecs_cpu_architecture

  spring_profile = var.ecs_spring_profile
  db_host        = module.database.db_address
  db_port        = module.database.db_port
  db_name        = module.database.db_name

  db_credentials_secret_arn = module.database.master_secret_arn

  environment_variables = {
    SIPSA_JWT_ISSUER_URI = module.cognito.issuer_url
  }

  secret_parameters = module.cognito.allowed_client_ids_parameter_arn != null ? {
    SIPSA_JWT_ALLOWED_CLIENT_IDS = module.cognito.allowed_client_ids_parameter_arn
  } : {}

  execution_ssm_parameter_arns = compact([module.cognito.allowed_client_ids_parameter_arn])

  log_retention_days = var.ecs_log_retention_days
}

# TECH-141 (ADR-010, partial TECH-132): internal ALB and ECS Service.
# desired_count stays at 1 — the in-process ingestion scheduler is not safe
# for multiple replicas (see modules/ecs-service/README.md).
#
# TECH-131: alb_allowed_ingress_cidr_blocks is populated with this stack's
# own private-application subnet CIDRs — the CIDR fallback TECH-141 already
# built for exactly this situation, chosen over
# alb_allowed_ingress_security_group_ids because module.api_gateway's NLB
# (fronting the VPC Link) deliberately has no security group of its own
# (see modules/api-gateway/README.md's "Architecture correction" section:
# AWS's docs do not confirm source-IP preservation for alb-type NLB
# targets, so a security-group reference isn't a defensible choice here).
# var.alb_allowed_ingress_cidr_blocks remains available for any additional,
# manually-approved CIDR beyond the VPC Link's own path.
module "ecs_service" {
  source = "../../modules/ecs-service"

  project_name = var.project_name
  environment  = var.environment
  common_tags  = local.common_tags

  vpc_id                 = module.network.vpc_id
  private_app_subnet_ids = module.network.private_app_subnet_ids

  ecs_cluster_id      = module.ecs_task.ecs_cluster_id
  task_definition_arn = module.ecs_task.task_definition_arn
  container_name      = module.ecs_task.container_name
  container_port      = module.ecs_task.container_port

  rds_security_group_id = module.database.db_security_group_id
  rds_port              = module.database.db_port

  alb_allowed_ingress_security_group_ids = var.alb_allowed_ingress_security_group_ids
  alb_allowed_ingress_cidr_blocks        = distinct(concat(var.private_app_subnet_cidrs, var.alb_allowed_ingress_cidr_blocks))
  alb_deletion_protection                = var.alb_deletion_protection
  enable_alb_access_logs                 = var.enable_alb_access_logs
  alb_access_logs_bucket                 = var.alb_access_logs_bucket
  alb_access_logs_prefix                 = var.alb_access_logs_prefix
  desync_mitigation_mode                 = var.alb_desync_mitigation_mode

  health_check_path                = var.health_check_path
  health_check_matcher             = var.health_check_matcher
  health_check_interval_seconds    = var.health_check_interval_seconds
  health_check_timeout_seconds     = var.health_check_timeout_seconds
  health_check_healthy_threshold   = var.health_check_healthy_threshold
  health_check_unhealthy_threshold = var.health_check_unhealthy_threshold

  desired_count                      = var.ecs_service_desired_count
  deployment_minimum_healthy_percent = var.ecs_deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.ecs_deployment_maximum_percent
  enable_execute_command             = var.ecs_enable_execute_command
  health_check_grace_period_seconds  = var.ecs_health_check_grace_period_seconds
}

# TECH-130 (ADR-010, layer 2 of ADR-002's Option E): Cognito user pool,
# resource server/scopes, M2M and human app clients. No API Gateway, VPC
# Link, or WAF exists yet — this module itself does not depend on
# module.network, module.ecs_task, or module.ecs_service at all (Cognito is
# not VPC-scoped). TECH-142 wires this module's issuer_url/
# allowed_client_ids_parameter_arn outputs INTO module.ecs_task (see that
# module block above, and modules/cognito/README.md's "Client-ID allowlist"
# section) — the dependency runs ecs_task -> cognito, never the reverse.
module "cognito" {
  source = "../../modules/cognito"

  project_name = var.project_name
  environment  = var.environment
  common_tags  = local.common_tags

  resource_server_identifier = var.cognito_resource_server_identifier
  resource_server_name       = var.cognito_resource_server_name

  mfa_configuration            = var.cognito_mfa_configuration
  advanced_security_mode       = var.cognito_advanced_security_mode
  deletion_protection          = var.cognito_deletion_protection
  allow_admin_create_user_only = var.cognito_allow_admin_create_user_only
  password_minimum_length      = var.cognito_password_minimum_length

  create_hosted_ui_domain = var.cognito_create_hosted_ui_domain
  cognito_domain_prefix   = var.cognito_domain_prefix

  human_callback_urls = var.cognito_human_callback_urls
  human_logout_urls   = var.cognito_human_logout_urls

  access_token_validity_minutes = var.cognito_access_token_validity_minutes
  id_token_validity_minutes     = var.cognito_id_token_validity_minutes
  refresh_token_validity_days   = var.cognito_refresh_token_validity_days

  publish_client_ids_to_ssm = var.cognito_publish_client_ids_to_ssm
}

# TECH-131 (ADR-010 Fase 4): API Gateway REST API, VPC Link (via an NLB
# chained to TECH-141's internal ALB — see modules/api-gateway/README.md's
# "Architecture correction" for why a plain "VPC Link to the ALB" is not
# how classic REST API VPC Links actually work), Cognito authorizer, API
# keys, usage plans, throttling, access logs. No custom domain, ACM, Route
# 53, or WAF.
module "api_gateway" {
  source = "../../modules/api-gateway"

  project_name = var.project_name
  environment  = var.environment
  common_tags  = local.common_tags

  vpc_id                 = module.network.vpc_id
  private_app_subnet_ids = module.network.private_app_subnet_ids
  alb_arn                = module.ecs_service.alb_arn
  alb_listener_port      = var.api_gateway_alb_listener_port
  alb_health_check_path  = var.health_check_path

  cognito_user_pool_arn              = module.cognito.user_pool_arn
  cognito_resource_server_identifier = module.cognito.resource_server_identifier

  endpoint_type = var.api_gateway_endpoint_type
  stage_name    = var.api_gateway_stage_name

  general_rate_limit            = var.api_gateway_general_rate_limit
  general_burst_limit           = var.api_gateway_general_burst_limit
  general_quota_limit           = var.api_gateway_general_quota_limit
  ingestion_trigger_rate_limit  = var.api_gateway_ingestion_trigger_rate_limit
  ingestion_trigger_burst_limit = var.api_gateway_ingestion_trigger_burst_limit

  access_log_retention_days = var.api_gateway_access_log_retention_days

  api_key_name = var.api_gateway_api_key_name

  cors_allowed_origins   = var.api_gateway_cors_allowed_origins
  cors_allow_credentials = var.api_gateway_cors_allow_credentials
}
