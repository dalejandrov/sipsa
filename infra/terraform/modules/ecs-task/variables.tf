variable "project_name" {
  description = "Short project identifier, forwarded into resource Name tags."
  type        = string
}

variable "environment" {
  description = "Deployment environment, forwarded into resource Name tags."
  type        = string
}

variable "aws_region" {
  description = "AWS region — used for the awslogs-region log driver option."
  type        = string
}

variable "common_tags" {
  description = "Tag set applied to every resource in this module."
  type        = map(string)
}

# --- Cluster -----------------------------------------------------------------

variable "enable_container_insights" {
  description = <<-EOT
    Whether ECS Container Insights is enabled on the cluster. Default true:
    Container Insights adds per-task/per-service CPU, memory, network, and
    storage metrics beyond the basic cluster-level metrics ECS provides for
    free — a real per-metric CloudWatch cost, but one this repository judges
    acceptable given this workload's operational importance (scheduled
    ingestion jobs with no human watching them run in real time) and the
    single-task, low-traffic scale keeping the absolute cost small. Revisit
    if cost pressure appears; document the reversal if so.
  EOT
  type        = bool
  default     = true
}

# --- Container image -----------------------------------------------------------

variable "ecr_repository_url" {
  description = "ECR repository URL (modules/ecr output) the task definition's image is built from."
  type        = string
}

variable "image_tag" {
  description = "Immutable image tag to deploy. Required, no default — \"latest\" is rejected outright since it is not a valid production contract against an IMMUTABLE-tag ECR repository (modules/ecr). For offline Terraform validation only, a placeholder such as \"unreleased\" may be used — never for a real deployment."
  type        = string

  validation {
    condition     = var.image_tag != "latest" && length(var.image_tag) > 0
    error_message = "image_tag must be set explicitly and must not be \"latest\"."
  }
}

variable "container_name" {
  description = "Container name within the task definition."
  type        = string
  default     = "sipsa-app"
}

variable "container_port" {
  description = "Port the application listens on. Default 8080 — confirmed from this repository's application.yaml (server.port bound to the PORT env var, default 8080) and Dockerfile (EXPOSE 8080), not assumed."
  type        = number
  default     = 8080

  validation {
    condition     = var.container_port >= 1 && var.container_port <= 65535
    error_message = "container_port must be between 1 and 65535."
  }
}

# --- Compute -------------------------------------------------------------------

variable "cpu" {
  description = <<-EOT
    Task-level CPU units (Fargate). Default 256 (.25 vCPU) — a PROPOSAL, not
    a confirmed capacity for real ingestion workloads. Must be validated
    against real heap/native-memory consumption, GC behavior, and the actual
    processing time for the largest known ingestion (Parcial, 229k+ records)
    before the first real deployment — see README.md.
  EOT
  type        = number
  default     = 256
}

variable "memory" {
  description = <<-EOT
    Task-level memory, in MiB (Fargate). Default 512 — a PROPOSAL, not a
    confirmed capacity. Must be validated the same way as `cpu` (see above)
    before the first real deployment — OOM risk from an under-sized value is
    a real failure mode for a large SOAP payload / large-batch upsert, not a
    theoretical one.
  EOT
  type        = number
  default     = 512
}

variable "cpu_architecture" {
  description = <<-EOT
    Fargate runtime platform CPU architecture. Default "X86_64" — this
    repository's CI (GitHub-hosted ubuntu-latest runners) builds x86_64
    images today, with no multi-arch build pipeline, ARM64 compatibility
    check for Java 25, or native-dependency audit performed. ARM64
    (Graviton) is a real future cost optimization, but requires that
    evidence first — not assumed here.
  EOT
  type        = string
  default     = "X86_64"

  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "cpu_architecture must be X86_64 or ARM64."
  }
}

# --- Application configuration ---------------------------------------------------

variable "spring_profile" {
  description = <<-EOT
    SPRING_PROFILES_ACTIVE for the container. Default "docker" — this
    repository has no dedicated "production" Spring profile
    (application-production.yaml does not exist); "docker"
    (application-docker.yaml) is the closest existing, already-safe analog:
    it inherits the production-safe baseline (no insecure defaults, no mock
    OIDC) and only overrides container-topology facts (the DB host default).
    Do not invent a new profile speculatively — introduce one deliberately,
    reviewing the existing profiles first, if a real need arises.
  EOT
  type        = string
  default     = "docker"
}

variable "db_host" {
  description = "Database host (RDS address, no port) — wire to modules/database's output at the root. Required, no default: this must be an explicit, real value at apply time, never guessed."
  type        = string
}

variable "db_port" {
  description = "Database port."
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "Database name."
  type        = string
}

variable "db_credentials_secret_arn" {
  description = <<-EOT
    Secrets Manager ARN containing `username`/`password` keys for the
    database connection. **This story wires it, at the root, to the RDS
    master secret (modules/database's master_secret_arn) as a temporary,
    explicitly-flagged placeholder** — not the final design. Per TECH-140's
    scope: no application-specific, minimum-privilege database user is
    created in this story (that requires a real SQL bootstrap step, out of
    scope here). A real deployment MUST replace this with a dedicated
    application credential before going live; using the master secret
    permanently would violate least-privilege — acceptable only for a
    later, explicitly-approved, temporary smoke test, never as the standing
    design.
  EOT
  type        = string
}

# --- Logs ------------------------------------------------------------------------

variable "log_retention_days" {
  description = "CloudWatch Logs retention, in days, for the application log group. No default of 0 (infinite) is permitted."
  type        = number
  default     = 30

  validation {
    condition     = var.log_retention_days > 0
    error_message = "log_retention_days must be positive — indefinite retention is not permitted."
  }
}

# --- IAM (execution / task role extensions) ---------------------------------------

variable "execution_extra_secret_arns" {
  description = "Additional Secrets Manager ARNs (beyond db_credentials_secret_arn) the execution role may read to resolve task-definition `secrets` entries — e.g. a future Cognito client secret once TECH-130 exists. Empty by default."
  type        = list(string)
  default     = []
}

variable "execution_ssm_parameter_arns" {
  description = "SSM Parameter Store ARNs the execution role may read to resolve task-definition `secrets` entries sourced from Parameter Store. Empty by default."
  type        = list(string)
  default     = []
}

variable "environment_variables" {
  description = <<-EOT
    Additional plain (non-secret) container environment variables, appended
    to this module's own fixed set (SPRING_PROFILES_ACTIVE, PORT, DB_HOST,
    DB_PORT, DB_NAME). Keyed by environment variable name. Empty by default.
    This module does not know about modules/cognito or any other specific
    module — the caller (environments/production) passes values through,
    e.g. wiring SIPSA_JWT_ISSUER_URI from module.cognito.issuer_url once a
    real identity provider exists (TECH-130). Keeps this module reusable
    without a direct dependency on which module produced the value.
  EOT
  type        = map(string)
  default     = {}
}

variable "secret_parameters" {
  description = <<-EOT
    Additional container `secrets` entries — resolved by the ECS agent at
    task start from Secrets Manager or SSM Parameter Store, never baked
    into the task definition or the image — appended to this module's own
    fixed set (DB_USERNAME/DB_PASSWORD from db_credentials_secret_arn).
    Keyed by environment variable name; each value is the full `valueFrom`
    (a Secrets Manager secret ARN, optionally with a JSON-key suffix, or an
    SSM parameter ARN/name). The corresponding ARN must also be granted via
    execution_extra_secret_arns/execution_ssm_parameter_arns for the
    execution role to actually resolve it — this variable only shapes the
    container definition, it does not grant IAM access. Empty by default.
    This module does not know about modules/cognito or any other specific
    module — e.g. wire SIPSA_JWT_ALLOWED_CLIENT_IDS here from
    module.cognito.allowed_client_ids_parameter_arn once a real identity
    provider exists (TECH-130).
  EOT
  type        = map(string)
  default     = {}
}

variable "task_role_policy_arns" {
  description = <<-EOT
    Managed IAM policy ARNs to attach to the task role (the role the
    application itself assumes at runtime, distinct from the execution
    role ECS itself uses to prepare the task). Empty by default: this
    application does not call any AWS API directly today (it uses
    Spring Data JPA against RDS via a database credential, not IAM), so the
    task role needs no permissions yet. Populate this only when the
    application itself is confirmed to need a specific AWS API — never
    speculatively.
  EOT
  type        = list(string)
  default     = []
}
