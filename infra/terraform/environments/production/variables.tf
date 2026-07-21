variable "project_name" {
  description = "Short project identifier used in resource names and tags."
  type        = string
  default     = "sipsa"
}

variable "environment" {
  description = "Deployment environment. ADR-010: production only for now — kept as a variable, not hardcoded, so a future environment does not require restructuring this module."
  type        = string
  default     = "production"
}

variable "aws_region" {
  description = "AWS region for every resource in this stack (ADR-010: us-east-1)."
  type        = string
  default     = "us-east-1"
}

variable "owner" {
  description = "Person or team accountable for this infrastructure (ADR-010: repository owner, initially)."
  type        = string
}

variable "cost_center" {
  description = "Cost center for billing attribution. No default — must not be invented; supply the real value at apply time."
  type        = string
}

variable "repository" {
  description = "Source repository for this infrastructure, for traceability in tags."
  type        = string
  default     = "dalejandrov/sipsa"
}

variable "managed_by" {
  description = "Tooling that manages these resources, for the ManagedBy tag."
  type        = string
  default     = "terraform"
}

# --- Network (TECH-138) ---------------------------------------------------
# Defaults match ADR-010's approved topology. Validation happens in the
# network module itself (the actual enforcement point); these are pass-
# through variables so the topology can be overridden per apply without
# editing module code.

variable "vpc_cidr" {
  description = "CIDR block for the production VPC (ADR-010/TECH-138: 10.40.0.0/16)."
  type        = string
  default     = "10.40.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for the two public subnets, one per AZ."
  type        = list(string)
  default     = ["10.40.0.0/24", "10.40.1.0/24"]
}

variable "private_app_subnet_cidrs" {
  description = "CIDR blocks for the two private application (ECS) subnets, one per AZ."
  type        = list(string)
  default     = ["10.40.10.0/24", "10.40.11.0/24"]
}

variable "private_database_subnet_cidrs" {
  description = "CIDR blocks for the two private database subnets, one per AZ."
  type        = list(string)
  default     = ["10.40.20.0/24", "10.40.21.0/24"]
}

variable "enable_vpc_flow_logs" {
  description = "Whether to create VPC Flow Logs. ADR-010/TECH-138: true for production."
  type        = bool
  default     = true
}

variable "flow_log_traffic_type" {
  description = "VPC Flow Log traffic type when enabled. ADR-010/TECH-138: REJECT (reduces volume, keeps the diagnostically useful signal)."
  type        = string
  default     = "REJECT"
}

variable "flow_log_retention_days" {
  description = "CloudWatch Logs retention, in days, for VPC Flow Logs when enabled. ADR-010/TECH-138: 30 days — never infinite."
  type        = number
  default     = 30
}

# --- Database (TECH-139) ---------------------------------------------------
# Defaults match ADR-010/TECH-139's approved topology. Validation happens in
# the database module itself; these are pass-through variables so the
# configuration can be overridden per apply without editing module code.

variable "postgres_engine_version" {
  description = "RDS PostgreSQL engine version. Default \"18\" — matches this repository's docker-compose.yml and Testcontainers image (postgres:18.0-alpine); NOT verified against a live AWS account (see modules/database/README.md)."
  type        = string
  default     = "18"
}

variable "db_auto_minor_version_upgrade" {
  description = "Whether RDS may auto-apply minor PostgreSQL upgrades during the maintenance window."
  type        = bool
  default     = true
}

variable "db_instance_class" {
  description = "RDS instance class. Default \"db.t3.micro\" — a PROPOSAL requiring availability validation before the first real apply (see modules/database/README.md)."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Initial RDS storage, in GiB. Default 20 (the gp3 minimum)."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "RDS storage autoscaling ceiling, in GiB. Default 100."
  type        = number
  default     = 100
}

variable "db_storage_type" {
  description = "RDS storage type. Default gp3 — no Provisioned IOPS at this stage."
  type        = string
  default     = "gp3"
}

variable "db_database_name" {
  description = "Initial database name."
  type        = string
  default     = "sipsa_db"
}

variable "db_master_username" {
  description = "RDS master username (an identifier, never a secret — the password is RDS-managed, see modules/database/README.md)."
  type        = string
  default     = "sipsa_admin"
}

variable "db_port" {
  description = "PostgreSQL port."
  type        = number
  default     = 5432
}

variable "db_backup_retention_period" {
  description = "Automated backup retention, in days. Default 7 (ADR-010)."
  type        = number
  default     = 7
}

variable "db_backup_window" {
  description = "Preferred daily backup window (UTC). Default \"06:00-06:30\" — outside the application's ingestion window (see modules/database/README.md for the explicit UTC conversion)."
  type        = string
  default     = "06:00-06:30"
}

variable "db_maintenance_window" {
  description = "Preferred weekly maintenance window (UTC). Default \"sun:07:00-sun:08:00\"."
  type        = string
  default     = "sun:07:00-sun:08:00"
}

variable "db_copy_tags_to_snapshot" {
  description = "Whether to copy instance tags to snapshots."
  type        = bool
  default     = true
}

variable "db_delete_automated_backups" {
  description = "Whether automated backups are deleted along with the instance."
  type        = bool
  default     = false
}

variable "db_deletion_protection" {
  description = "Whether RDS deletion protection is enabled. Default true (ADR-010)."
  type        = bool
  default     = true
}

variable "db_skip_final_snapshot" {
  description = "Whether to skip the final snapshot on deletion. Default false."
  type        = bool
  default     = false
}

variable "db_multi_az" {
  description = "Whether RDS Multi-AZ is enabled. Default false (ADR-010: Single-AZ initially, a documented cost trade-off — see modules/database/README.md)."
  type        = bool
  default     = false
}

variable "db_performance_insights_enabled" {
  description = "Whether RDS Performance Insights is enabled. Default false."
  type        = bool
  default     = false
}

variable "db_monitoring_interval" {
  description = "RDS Enhanced Monitoring granularity, in seconds (0 disables it). Default 0."
  type        = number
  default     = 0
}

variable "db_enabled_cloudwatch_logs_exports" {
  description = "RDS log types exported to CloudWatch. Default [\"postgresql\", \"upgrade\"]."
  type        = list(string)
  default     = ["postgresql", "upgrade"]
}

variable "db_log_retention_days" {
  description = "CloudWatch Logs retention, in days, for RDS log exports. Default 30 — never infinite."
  type        = number
  default     = 30
}

# --- Container registry (TECH-140) ------------------------------------------

variable "ecr_encryption_type" {
  description = "ECR encryption type. Default AES256."
  type        = string
  default     = "AES256"
}

variable "ecr_kms_key_id" {
  description = "KMS key ID/ARN for ECR encryption, only used when ecr_encryption_type is \"KMS\". No default."
  type        = string
  default     = null
}

variable "ecr_keep_last_tagged_images" {
  description = "Number of tagged images to retain in ECR. Default 20."
  type        = number
  default     = 20
}

variable "ecr_expire_untagged_after_days" {
  description = "Days after which an untagged ECR image expires. Default 7."
  type        = number
  default     = 7
}

# --- ECS cluster and task definition (TECH-140) -----------------------------

variable "ecs_enable_container_insights" {
  description = "Whether ECS Container Insights is enabled. Default true (see modules/ecs-task/README.md for the cost/observability trade-off)."
  type        = bool
  default     = true
}

variable "ecs_image_tag" {
  description = "Immutable image tag for the application container. No default — must be supplied explicitly; \"latest\" is rejected by the ecs-task module. For offline Terraform validation only, a placeholder such as \"unreleased\" may be used."
  type        = string
  default     = "unreleased"
}

variable "ecs_container_name" {
  description = "Container name within the task definition."
  type        = string
  default     = "sipsa-app"
}

variable "ecs_container_port" {
  description = "Port the application listens on. Default 8080, confirmed from application.yaml and the Dockerfile."
  type        = number
  default     = 8080
}

variable "ecs_task_cpu" {
  description = "Task-level CPU units (Fargate). Default 256 — a PROPOSAL requiring validation against real ingestion workload consumption before the first real deployment (see modules/ecs-task/README.md)."
  type        = number
  default     = 256
}

variable "ecs_task_memory" {
  description = "Task-level memory, in MiB (Fargate). Default 512 — a PROPOSAL requiring the same validation as ecs_task_cpu."
  type        = number
  default     = 512
}

variable "ecs_cpu_architecture" {
  description = "Fargate CPU architecture. Default \"X86_64\" — this repository's CI builds x86_64 images today; ARM64 is a future optimization requiring evidence first (see modules/ecs-task/README.md)."
  type        = string
  default     = "X86_64"
}

variable "ecs_spring_profile" {
  description = "SPRING_PROFILES_ACTIVE for the container. Default \"docker\" — this repository has no dedicated production profile; \"docker\" is the closest existing, already-safe analog (see modules/ecs-task/README.md)."
  type        = string
  default     = "docker"
}

variable "ecs_log_retention_days" {
  description = "CloudWatch Logs retention, in days, for the application log group. Default 30 — never infinite."
  type        = number
  default     = 30
}
