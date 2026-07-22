variable "project_name" {
  description = "Short project identifier, forwarded into resource Name tags."
  type        = string
}

variable "environment" {
  description = "Deployment environment, forwarded into resource Name tags."
  type        = string
}

variable "common_tags" {
  description = "Tag set applied to every resource in this module that supports tagging."
  type        = map(string)
}

# --- Network — private connection to the internal ALB (TECH-132/TECH-141) ----

variable "vpc_id" {
  description = "VPC ID the NLB (VPC Link target) is created in."
  type        = string
}

variable "private_app_subnet_ids" {
  description = "Private application subnet IDs — the NLB fronting the VPC Link is placed here, the same tier as the internal ALB and ECS Service it targets."
  type        = list(string)
}

variable "alb_arn" {
  description = "ARN of the existing internal ALB (modules/ecs-service output) — the single target of the NLB's target group. AWS only supports one ALB per target group of type \"alb\"."
  type        = string
}

variable "alb_listener_port" {
  description = "Port of the internal ALB's own listener. Must match the NLB target group's port exactly (AWS requirement for an ALB-type target group)."
  type        = number
  default     = 80
}

variable "alb_health_check_path" {
  description = "Health check path forwarded by the NLB to the ALB, then to its own target group. Same path already used at the ALB layer (modules/ecs-service), for consistency — not a new health check contract."
  type        = string
  default     = "/actuator/health"
}

# --- Cognito authorizer (TECH-130) --------------------------------------------

variable "cognito_user_pool_arn" {
  description = "ARN of the existing Cognito user pool (modules/cognito output). No new user pool is created by this module."
  type        = string
}

variable "cognito_resource_server_identifier" {
  description = "Resource server identifier (modules/cognito output, \"sipsa\" by default) — the scope prefix used to build each method's authorization_scopes."
  type        = string
}

# --- REST API ------------------------------------------------------------------

variable "endpoint_type" {
  description = "API Gateway REST API endpoint configuration type. \"REGIONAL\" — no CloudFront distribution needed for a VPC-Link-only backend; \"EDGE\" would add latency-irrelevant global caching this API doesn't need."
  type        = string
  default     = "REGIONAL"

  validation {
    condition     = contains(["REGIONAL", "EDGE", "PRIVATE"], var.endpoint_type)
    error_message = "endpoint_type must be REGIONAL, EDGE, or PRIVATE."
  }
}

variable "stage_name" {
  description = "API Gateway stage name. \"production\" — this repository has one AWS environment (ADR-010)."
  type        = string
  default     = "production"
}

# --- Throttling and quota (ADR-010 approved values) ---------------------------

variable "general_rate_limit" {
  description = "Stage-wide default requests/second, applied to every route except the ingestion-trigger overrides. ADR-010 approved value: 10."
  type        = number
  default     = 10
}

variable "general_burst_limit" {
  description = "Stage-wide default burst capacity. ADR-010 approved value: 20."
  type        = number
  default     = 20
}

variable "general_quota_limit" {
  description = "Usage plan monthly request quota per consumer. ADR-010 approved value: 100000."
  type        = number
  default     = 100000
}

variable "ingestion_trigger_rate_limit" {
  description = "Requests/second for the ingestion-trigger routes only (POST run, POST cancel) — stricter than the general default. ADR-010 approved value: 1."
  type        = number
  default     = 1
}

variable "ingestion_trigger_burst_limit" {
  description = "Burst capacity for the ingestion-trigger routes only. ADR-010 approved value: 2."
  type        = number
  default     = 2
}

# --- Access logs -----------------------------------------------------------------

variable "access_log_retention_days" {
  description = "CloudWatch Logs retention, in days, for API Gateway access logs. Default 30, consistent with this repository's other log groups. No default of 0 (infinite) permitted."
  type        = number
  default     = 30

  validation {
    condition     = var.access_log_retention_days > 0
    error_message = "access_log_retention_days must be positive — indefinite retention is not permitted."
  }
}

# --- CORS — undecided browser-client requirement (aws-production-readiness.md §1.6) --

variable "cors_allowed_origins" {
  description = <<-EOT
    Origins allowed to call GET /api/sipsa/** with credentialed CORS
    requests. Empty by default — no browser-client requirement has been
    established anywhere in this repository (see
    docs/architecture/aws-production-readiness.md §1.6); CORS stays
    disabled (no aws_api_gateway_method for OPTIONS, no
    Access-Control-Allow-Origin response header) until a real origin is
    confirmed. No wildcard ("*") is ever accepted here when
    cors_allow_credentials is true — validated below. Never invent a
    frontend domain to fill this in.
  EOT
  type        = list(string)
  default     = []

  validation {
    condition     = length(var.cors_allowed_origins) <= 1
    error_message = "cors_allowed_origins supports at most one origin natively (this module's CORS response header is a static value via aws_api_gateway_method_response, not a per-request dynamic Origin echo — that would require a Lambda proxy integration, out of scope while no real origin is confirmed at all)."
  }
}

variable "api_key_name" {
  description = <<-EOT
    Name of the one API key this module provisions for the general usage
    plan (GET /api/sipsa/** consumers) — one parameterizable resource, the
    same "one client now, extensible later" precedent modules/cognito
    already established for the M2M app client, not several speculative
    consumers invented here. No value is set (AWS generates it); see this
    module's README.md for the retrieval/distribution strategy.
  EOT
  type        = string
  default     = "sipsa-primary-consumer"
}

variable "cors_allow_credentials" {
  description = "Whether CORS responses include Access-Control-Allow-Credentials: true. Default false. When true, cors_allowed_origins must not contain \"*\" (the browser CORS spec itself forbids combining a wildcard origin with credentials)."
  type        = bool
  default     = false

  validation {
    condition     = !var.cors_allow_credentials || !contains(var.cors_allowed_origins, "*")
    error_message = "cors_allowed_origins must not contain \"*\" when cors_allow_credentials is true — forbidden by the CORS specification itself, not just this module's policy."
  }
}
