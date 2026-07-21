variable "project_name" {
  description = "Short project identifier, forwarded into resource Name tags."
  type        = string
}

variable "environment" {
  description = "Deployment environment, forwarded into resource Name tags."
  type        = string
}

variable "common_tags" {
  description = "Tag set applied to every resource in this module, on top of a resource-specific Name tag."
  type        = map(string)
}

variable "vpc_id" {
  description = "ID of the VPC this database's security group belongs to (TECH-138's network module output)."
  type        = string
}

variable "private_database_subnet_ids" {
  description = "IDs of the private database subnets (TECH-138's network module output) — this module's DB subnet group uses these exclusively, never public or private-application subnets."
  type        = list(string)

  validation {
    condition     = length(var.private_database_subnet_ids) >= 2
    error_message = "At least two private database subnet IDs are required (RDS DB subnet groups must span at least two Availability Zones)."
  }
}

# --- Access control ---------------------------------------------------------

variable "allowed_security_group_ids" {
  description = <<-EOT
    Security group IDs allowed to reach this database on `var.port`. Empty by
    default (TECH-139): this story creates the RDS security group but adds
    NO ingress rule at all, since no consumer (ECS) security group exists
    yet. A future story (TECH-132's compute phase) passes its ECS service's
    security group ID here, which creates exactly one ingress rule per ID —
    never a CIDR-based rule, never 0.0.0.0/0.
  EOT
  type        = list(string)
  default     = []
}

variable "port" {
  description = "PostgreSQL port. Never exposed publicly regardless of this value (publicly_accessible is hardcoded false)."
  type        = number
  default     = 5432

  validation {
    condition     = var.port >= 1 && var.port <= 65535
    error_message = "port must be between 1 and 65535."
  }
}

# --- Engine ------------------------------------------------------------------

variable "postgres_engine_version" {
  description = <<-EOT
    PostgreSQL engine version for RDS. Defaults to "18" (major version only,
    letting AWS resolve the latest supported minor within it) — the same
    major version this repository's docker-compose.yml and every
    Testcontainers-based integration test already use (postgres:18.0-alpine,
    confirmed by inspecting the repository, not assumed). RDS's actual
    support for engine_version "18" has NOT been verified against a live AWS
    account by this story (no AWS API call is made) — confirm via
    `aws rds describe-db-engine-versions --engine postgres` or the AWS
    console before the first real apply.
  EOT
  type        = string
  default     = "18"

  validation {
    condition     = length(var.postgres_engine_version) > 0
    error_message = "postgres_engine_version must not be empty."
  }
}

variable "auto_minor_version_upgrade" {
  description = "Whether RDS may automatically apply minor version upgrades during the maintenance window. Default true: minor PostgreSQL upgrades are backward-compatible patch releases (security fixes, bug fixes), and this repository has no evidence of a reason to pin a minor version manually."
  type        = bool
  default     = true
}

# --- Compute and storage -----------------------------------------------------

variable "instance_class" {
  description = <<-EOT
    RDS instance class. Defaults to "db.t3.micro" — a burstable, non-Graviton
    (Intel/AMD) class proposed for this initial, low/near-zero-traffic stage.
    Explicitly NOT a Graviton class (e.g. db.t4g.*): Graviton availability
    and PostgreSQL-18 compatibility for this specific class have not been
    confirmed against a live AWS account by this story. This default is a
    PROPOSAL requiring validation (class availability in us-east-1 for the
    chosen engine_version) before the first real apply — not a confirmed
    fact.
  EOT
  type        = string
  default     = "db.t3.micro"
}

variable "allocated_storage" {
  description = "Initial allocated storage, in GiB. Default 20 — the minimum valid value for gp3 storage on RDS PostgreSQL."
  type        = number
  default     = 20

  validation {
    condition     = var.allocated_storage >= 20
    error_message = "allocated_storage must be at least 20 GiB (the RDS PostgreSQL gp3 minimum)."
  }
}

variable "max_allocated_storage" {
  description = <<-EOT
    Ceiling for RDS storage autoscaling, in GiB. Default 100 — bounds
    unlimited growth; storage autoscaling cost is proportional to actual
    usage up to this ceiling, so raising it raises the worst-case cost, not
    the typical one, but a ceiling must still exist rather than being left
    unbounded.
  EOT
  type        = number
  default     = 100

  validation {
    condition     = var.max_allocated_storage > var.allocated_storage
    error_message = "max_allocated_storage must be greater than allocated_storage."
  }
}

variable "storage_type" {
  description = "RDS storage type. Default gp3 (general-purpose SSD) — no Provisioned IOPS (io1/io2) at this stage; gp3's baseline throughput/IOPS are more than sufficient for the traffic this application generates today."
  type        = string
  default     = "gp3"

  validation {
    condition     = contains(["gp3", "gp2"], var.storage_type)
    error_message = "storage_type must be gp3 or gp2 — Provisioned IOPS (io1/io2) is not used at this stage."
  }
}

# --- Database and credentials -------------------------------------------------

variable "database_name" {
  description = "Initial database name, created when the instance is provisioned."
  type        = string
  default     = "sipsa_db"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$", var.database_name))
    error_message = "database_name must start with a letter and contain only letters, digits, and underscores (max 63 characters)."
  }
}

variable "master_username" {
  description = "Master username for the RDS instance. Must not be a personal name, a reserved PostgreSQL/RDS word (e.g. \"postgres\", \"admin\", \"rdsadmin\"), and must not appear in any Secrets Manager value this repository manages directly — the password itself is never a Terraform variable (see README.md: manage_master_user_password = true)."
  type        = string
  default     = "sipsa_admin"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]{0,62}$", var.master_username))
    error_message = "master_username must start with a letter and contain only letters, digits, and underscores (max 63 characters)."
  }
}

# --- Backups and maintenance ---------------------------------------------------

variable "backup_retention_period" {
  description = "Automated backup retention, in days. Default 7 (ADR-010)."
  type        = number
  default     = 7

  validation {
    condition     = var.backup_retention_period >= 1 && var.backup_retention_period <= 35
    error_message = "backup_retention_period must be between 1 and 35 days (RDS's own limit)."
  }
}

variable "backup_window" {
  description = <<-EOT
    Preferred daily backup window, in UTC, format "HH:MM-HH:MM". Default
    "06:00-06:30" — chosen to fall entirely outside this application's daily
    ingestion window. The scheduler's daily ingestion window is 14:20-23:59
    America/Bogota (UTC-5), i.e. 19:20 UTC to 04:59 UTC the next day; monthly
    jobs (days 8 and 10) fire within that same daily window at 14:30
    Bogota = 19:30 UTC. 06:00-06:30 UTC = 01:00-01:30 AM Bogota, comfortably
    inside the remaining 05:00-19:19 UTC gap on every day of the month.
  EOT
  type        = string
  default     = "06:00-06:30"

  validation {
    condition     = can(regex("^([01][0-9]|2[0-3]):[0-5][0-9]-([01][0-9]|2[0-3]):[0-5][0-9]$", var.backup_window))
    error_message = "backup_window must be in the format HH:MM-HH:MM (UTC)."
  }
}

variable "maintenance_window" {
  description = <<-EOT
    Preferred weekly maintenance window, in UTC, format
    "ddd:HH:MM-ddd:HH:MM". Default "sun:07:00-sun:08:00" — Sunday 07:00-08:00
    UTC = Sunday 02:00-03:00 AM Bogota, outside the daily ingestion window on
    every day (see backup_window's description for the UTC conversion) and
    deliberately not overlapping the backup window above.
  EOT
  type        = string
  default     = "sun:07:00-sun:08:00"

  validation {
    condition     = can(regex("^(mon|tue|wed|thu|fri|sat|sun):([01][0-9]|2[0-3]):[0-5][0-9]-(mon|tue|wed|thu|fri|sat|sun):([01][0-9]|2[0-3]):[0-5][0-9]$", var.maintenance_window))
    error_message = "maintenance_window must be in the format ddd:HH:MM-ddd:HH:MM (UTC), e.g. sun:07:00-sun:08:00."
  }
}

variable "copy_tags_to_snapshot" {
  description = "Whether to copy the instance's tags to any snapshot taken of it. Default true — keeps snapshots attributable via the same tagging convention as the instance itself."
  type        = bool
  default     = true
}

variable "delete_automated_backups" {
  description = "Whether automated backups are deleted when the instance is deleted. Default false — retains automated backups past instance deletion as a recovery safety net; a deliberate choice, not the RDS default."
  type        = bool
  default     = false
}

variable "deletion_protection" {
  description = "Whether RDS deletion protection is enabled. Default true (ADR-010) — an explicit `terraform apply` with this set to false is required before the instance can be destroyed at all."
  type        = bool
  default     = true
}

variable "skip_final_snapshot" {
  description = "Whether to skip taking a final snapshot on deletion. Default false — a final snapshot is taken (see main.tf for the reproducible, unique snapshot-identifier strategy)."
  type        = bool
  default     = false
}

# --- High availability --------------------------------------------------------

variable "multi_az" {
  description = <<-EOT
    Whether RDS Multi-AZ is enabled. Default false (ADR-010): Single-AZ is a
    deliberate, cost-driven initial decision, not an oversight — Multi-AZ
    roughly doubles the instance cost for automatic failover this stage's
    traffic and criticality do not yet justify. Documented accepted risk:
    no automatic failover; an AZ outage or maintenance event affecting the
    single instance causes a full outage until AWS completes recovery.
    Promote to true once real usage or a criticality decision justifies the
    cost — this variable makes that a one-line change, not a redesign.
  EOT
  type        = bool
  default     = false
}

# --- Monitoring and logs -------------------------------------------------------

variable "performance_insights_enabled" {
  description = "Whether RDS Performance Insights is enabled. Default false — an added cost with no established need yet at this stage; enable once real query-performance visibility is required."
  type        = bool
  default     = false
}

variable "monitoring_interval" {
  description = "RDS Enhanced Monitoring granularity, in seconds (0 disables it). Default 0 (disabled) — an added cost (and a dedicated IAM role) with no established need yet; standard CloudWatch metrics remain available regardless."
  type        = number
  default     = 0

  validation {
    condition     = contains([0, 1, 5, 10, 15, 30, 60], var.monitoring_interval)
    error_message = "monitoring_interval must be one of: 0 (disabled), 1, 5, 10, 15, 30, 60."
  }
}

variable "enabled_cloudwatch_logs_exports" {
  description = <<-EOT
    RDS log types exported to CloudWatch Logs. Default ["postgresql",
    "upgrade"] — both are low-volume, operationally useful signals (general
    server log, and upgrade history). Query-level or audit logging (e.g. via
    pgaudit or log_statement=all) is NOT enabled here — that volume has not
    been measured against this application's real traffic, and enabling it
    by default risks meaningful, unbudgeted log-ingestion cost.
  EOT
  type        = list(string)
  default     = ["postgresql", "upgrade"]

  validation {
    condition     = alltrue([for t in var.enabled_cloudwatch_logs_exports : contains(["postgresql", "upgrade"], t)])
    error_message = "enabled_cloudwatch_logs_exports may only contain \"postgresql\" and/or \"upgrade\" — the two log types RDS for PostgreSQL supports exporting."
  }
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention, in days, for any log group this module creates. No default of 0 (infinite) is permitted."
  type        = number
  default     = 30

  validation {
    condition     = var.log_retention_days > 0
    error_message = "log_retention_days must be positive — indefinite retention is not permitted."
  }
}
