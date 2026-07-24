variable "project_name" {
  description = "Short project identifier, forwarded into resource Name tags."
  type        = string
}

variable "environment" {
  description = "Deployment environment, forwarded into resource Name tags."
  type        = string
}

variable "common_tags" {
  description = "Tag set applied to every resource in this module."
  type        = map(string)
}

variable "vpc_id" {
  description = "ID of the VPC (TECH-138's network module output)."
  type        = string
}

variable "private_app_subnet_ids" {
  description = <<-EOT
    IDs of the private application subnets (TECH-138's network module
    output). Both the internal ALB and the ECS Service use these
    exclusively — never public subnets. The ALB is placed in the same
    tier as the ECS tasks it fronts (not a dedicated ALB subnet tier):
    this repository has no other workload in the private-application
    tier, so a separate tier would add subnet/route-table complexity
    without a corresponding isolation benefit — see README.md.
  EOT
  type        = list(string)

  validation {
    condition     = length(var.private_app_subnet_ids) >= 2
    error_message = "At least two private application subnet IDs are required (the ALB must span at least two Availability Zones)."
  }
}

# --- Upstream module wiring (never redeclared here) --------------------------

variable "ecs_cluster_id" {
  description = "ECS cluster ID (modules/ecs-task output). This module does not create a cluster."
  type        = string
}

variable "task_definition_arn" {
  description = "Task definition ARN (modules/ecs-task output). This module does not build or redeclare the task definition — responsibilities stay separated."
  type        = string
}

variable "container_name" {
  description = "Container name within the task definition (modules/ecs-task output) — must match exactly for the ECS Service's load_balancer block to attach correctly."
  type        = string
}

variable "container_port" {
  description = "Port the container listens on (modules/ecs-task output — 8080, confirmed from this repository's application.yaml/Dockerfile)."
  type        = number
}

variable "rds_security_group_id" {
  description = "RDS security group ID (modules/database output). This module adds the ECS -> RDS ingress rule directly on it — the database module itself creates no ingress rule (see modules/database/README.md)."
  type        = string
}

variable "rds_port" {
  description = "RDS port (modules/database output — 5432)."
  type        = number
  default     = 5432
}

# --- ALB ingress (deliberately closed by default) -----------------------------

variable "alb_allowed_ingress_security_group_ids" {
  description = <<-EOT
    Security group IDs allowed to reach the ALB on port 80. Empty by
    default (TECH-141): no VPC Link security group exists yet — TECH-131
    populates this once API Gateway's VPC Link is created. This is the
    preferred mechanism; alb_allowed_ingress_cidr_blocks below is a
    documented fallback, not the default path.
  EOT
  type        = list(string)
  default     = []
}

variable "alb_allowed_ingress_cidr_blocks" {
  description = <<-EOT
    CIDR blocks allowed to reach the ALB on port 80, as a documented
    fallback if a security-group-based rule isn't yet possible (e.g. a
    temporary, explicitly-approved allowance for the VPC's own CIDR
    during integration testing). Empty by default — 0.0.0.0/0 is
    rejected outright by validation; this must never be the ALB's
    public exposure path, since the ALB is internal and API Gateway is
    the only intended public entry point (ADR-002).
  EOT
  type        = list(string)
  default     = []

  validation {
    condition     = !contains(var.alb_allowed_ingress_cidr_blocks, "0.0.0.0/0")
    error_message = "alb_allowed_ingress_cidr_blocks must never contain 0.0.0.0/0 — the ALB is internal; API Gateway (TECH-131) is the only intended public entry point."
  }
}

# --- ALB configuration ---------------------------------------------------------

variable "alb_deletion_protection" {
  description = "Whether ALB deletion protection is enabled. Default true, consistent with this repository's RDS deletion-protection posture (ADR-010)."
  type        = bool
  default     = true
}

variable "enable_alb_access_logs" {
  description = <<-EOT
    Whether ALB access logs are enabled. Default false: enabling them
    requires a real S3 bucket with a correct access policy, encryption,
    and retention — none of which this story creates (creating an ad
    hoc, possibly-insecure bucket just to flip this on would be worse
    than leaving it off). Follow-up: API Gateway's own access logs are
    mandatory in TECH-131 regardless; revisit this ALB-level logging
    before any public exposure if gateway-level logs prove insufficient
    for a specific investigation.
  EOT
  type        = bool
  default     = false
}

variable "alb_access_logs_bucket" {
  description = "S3 bucket name for ALB access logs. Required only if enable_alb_access_logs is true. No default — must not be invented."
  type        = string
  default     = null

  validation {
    condition     = !var.enable_alb_access_logs || var.alb_access_logs_bucket != null
    error_message = "alb_access_logs_bucket must be set when enable_alb_access_logs is true."
  }
}

variable "alb_access_logs_prefix" {
  description = "S3 key prefix for ALB access logs, only used when enable_alb_access_logs is true."
  type        = string
  default     = ""
}

variable "desync_mitigation_mode" {
  description = "ALB desync mitigation mode. Default \"defensive\" — AWS's own recommended default; \"strictest\" is available but risks rejecting legitimate-but-non-strictly-compliant requests without evidence that's needed here."
  type        = string
  default     = "defensive"

  validation {
    condition     = contains(["monitor", "defensive", "strictest"], var.desync_mitigation_mode)
    error_message = "desync_mitigation_mode must be one of: monitor, defensive, strictest."
  }
}

# --- Health check ---------------------------------------------------------------

variable "health_check_path" {
  description = <<-EOT
    Target group health check path. Default "/actuator/health" —
    confirmed safe to use unauthenticated: SecurityConfig permits it
    outright (permitAll()), and management.endpoint.health.show-details
    is "when-authorized" in application.yaml, meaning an unauthenticated
    caller (the ALB) receives only the top-level UP/DOWN status, never
    component details. No alternative endpoint was invented — this is
    the application's own real, already-safe health endpoint.
  EOT
  type        = string
  default     = "/actuator/health"
}

variable "health_check_matcher" {
  description = "Expected HTTP status code(s) for a healthy target."
  type        = string
  default     = "200"
}

variable "health_check_interval_seconds" {
  description = "Seconds between health checks."
  type        = number
  default     = 30
}

variable "health_check_timeout_seconds" {
  description = "Seconds to wait for a health check response before marking it failed."
  type        = number
  default     = 5
}

variable "health_check_healthy_threshold" {
  description = "Consecutive successful health checks before a target is considered healthy."
  type        = number
  default     = 3
}

variable "health_check_unhealthy_threshold" {
  description = "Consecutive failed health checks before a target is considered unhealthy."
  type        = number
  default     = 3
}

# --- ECS Service -----------------------------------------------------------------

variable "desired_count" {
  description = <<-EOT
    Number of tasks the ECS Service maintains. Default 1 — deliberately
    not higher: this application's scheduler (SIPSA's ingestion cron
    jobs) runs inside the application process itself, with no leader
    election or distributed lock. A second concurrently-running task
    would run the scheduler twice, double-triggering every ingestion
    job. Do NOT raise this above 1 until leader election, an external
    scheduler, or a scheduler/API process split exists — see
    README.md "Scheduler and multiple replicas."
  EOT
  type        = number
  default     = 1

  validation {
    condition     = var.desired_count >= 1
    error_message = "desired_count must be at least 1."
  }
}

variable "deployment_minimum_healthy_percent" {
  description = "Minimum percent of desired_count that must remain healthy during a deployment. Default 100 — with desired_count=1 and maximum_percent=200, this allows ECS to start a new task before stopping the old one, never going below 1 healthy task."
  type        = number
  default     = 100
}

variable "deployment_maximum_percent" {
  description = "Maximum percent of desired_count allowed to run during a deployment. Default 200 — permits one extra task during rollout (2 tasks briefly, with desired_count=1) so the old task keeps serving until the new one is healthy."
  type        = number
  default     = 200
}

variable "enable_execute_command" {
  description = <<-EOT
    Whether ECS Exec is enabled on the service. Default false: ECS Exec
    requires its own IAM permissions, session logging, and audit
    posture to be defined deliberately before enabling — turning it on
    without that design would grant an interactive shell into the
    production container with no corresponding control. Revisit as its
    own, explicit decision, not a default.
  EOT
  type        = bool
  default     = false
}

variable "health_check_grace_period_seconds" {
  description = <<-EOT
    Seconds ECS waits after a task starts before counting a failed ALB
    health check against it. Default 480 (TECH-144: measured locally,
    replacing the earlier unmeasured 120 guess). Six real local Docker
    runs of the real application image (three at 512 MiB, three at
    1024 MiB, both under 0.25 vCPU) reached /actuator/health 200 after
    187s/188s/207s/214s/221s/385s — min 187s, median ~210.5s, max 385s.
    The 385s sample is real and kept, not discarded: Spring Boot's own
    "Started SipsaApplication" log for that run reported ~192s,
    consistent with the other five samples, so the extra gap before the
    host's curl-based probe succeeded is most likely local Docker
    Desktop network/host contention from concurrent heavy Docker usage
    during the measurement session — not confirmed, not assumed away.
    480 gives ~95s margin over the worst observed sample. See
    docs/operations/aws-production-preflight.md for the full data.
    Measured on local Docker Desktop (macOS/ARM64), not real AWS Fargate
    hardware — still requires confirmation against a real deployment;
    too high a value would hide a genuinely broken startup for that
    long, so this is not padded further "to be safe" beyond that margin.
  EOT
  type        = number
  default     = 480

  validation {
    condition     = var.health_check_grace_period_seconds >= 0
    error_message = "health_check_grace_period_seconds must not be negative."
  }
}
