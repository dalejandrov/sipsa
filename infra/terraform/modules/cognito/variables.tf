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

# --- Resource server and scopes ---------------------------------------------

variable "resource_server_identifier" {
  description = <<-EOT
    Resource server identifier. Default "sipsa" — matches the scope prefix
    ("sipsa/ingestion.execute", etc.) SecurityConfig already enforces
    (src/main/java/.../infrastructure/config/security/SecurityConfig.java).
    Not invented: confirmed by grep against the real code, not chosen
    independently.
  EOT
  type        = string
  default     = "sipsa"
}

variable "resource_server_name" {
  description = "Human-readable resource server name shown in the Cognito console."
  type        = string
  default     = "SIPSA API"
}

# --- User pool ---------------------------------------------------------------

variable "mfa_configuration" {
  description = <<-EOT
    Cognito MFA configuration. Default "OPTIONAL": human users exist, but no
    operational enrollment/recovery flow for MFA exists yet (no frontend, no
    documented support process for a locked-out user) — forcing MFA now
    would create an operational dead end. "OPTIONAL" lets it be adopted
    per-user without blocking anyone. Document mandatory MFA as a future
    hardening step once real risk (e.g. a specific incident, or a
    compliance requirement) justifies the operational cost of building that
    support flow first.
  EOT
  type        = string
  default     = "OPTIONAL"

  validation {
    condition     = contains(["OFF", "ON", "OPTIONAL"], var.mfa_configuration)
    error_message = "mfa_configuration must be one of: OFF, ON, OPTIONAL."
  }
}

variable "advanced_security_mode" {
  description = <<-EOT
    Cognito advanced security features mode. Default "AUDIT": provides
    risk-based visibility (compromised-credential checks, adaptive
    authentication signals) without blocking or challenging any sign-in —
    "ENFORCED" would add friction with no evidence yet that it's needed,
    and advanced security features carry a real per-MAU cost regardless of
    which non-OFF mode is chosen. "OFF" gives no visibility at all. AUDIT
    is the middle ground: observe first, decide whether ENFORCED is
    justified once real usage and real cost data exist.
  EOT
  type        = string
  default     = "AUDIT"

  validation {
    condition     = contains(["OFF", "AUDIT", "ENFORCED"], var.advanced_security_mode)
    error_message = "advanced_security_mode must be one of: OFF, AUDIT, ENFORCED."
  }
}

variable "deletion_protection" {
  description = "Cognito user pool deletion protection (\"ACTIVE\"/\"INACTIVE\", not a bool). Default \"ACTIVE\", consistent with this repository's RDS/ALB deletion-protection posture (ADR-010)."
  type        = string
  default     = "ACTIVE"

  validation {
    condition     = contains(["ACTIVE", "INACTIVE"], var.deletion_protection)
    error_message = "deletion_protection must be ACTIVE or INACTIVE."
  }
}

variable "allow_admin_create_user_only" {
  description = <<-EOT
    Whether only an administrator can create user pool accounts (public
    self-registration disabled). Default true: SIPSA is an internal
    operational tool for a DANE-integration team, not a public-signup
    product — there is no documented requirement for open self-registration,
    and admin-provisioned accounts is the more conservative default for
    that kind of user base. Revisit explicitly if a real self-service
    sign-up requirement emerges.
  EOT
  type        = bool
  default     = true
}

variable "password_minimum_length" {
  description = "Minimum password length for human users."
  type        = number
  default     = 12
}

# --- Hosted UI domain (optional — Authorization Code needs one to be usable) --

variable "create_hosted_ui_domain" {
  description = <<-EOT
    Whether to create a Cognito-managed (prefix) domain for the Hosted UI.
    Default false: no frontend and no approved callback URL exists yet, so
    a Hosted UI domain would have nothing real to redirect to. The human
    app client's Authorization Code + PKCE configuration is still created
    when this is false — it simply has no reachable authorization endpoint
    until a domain exists. Enabling this later is a one-variable-flip plus
    cognito_domain_prefix, not a restructuring. No custom domain, no ACM
    certificate, no Route 53 record — a Cognito-managed prefix domain only.
  EOT
  type        = bool
  default     = false
}

variable "cognito_domain_prefix" {
  description = <<-EOT
    Cognito Hosted UI domain prefix (must be globally unique across all of
    AWS, not just this account — the same uniqueness class as an S3 bucket
    name). No default is invented here; required only when
    create_hosted_ui_domain is true. Choose the real value at apply time.
  EOT
  type        = string
  default     = null

  validation {
    condition     = !var.create_hosted_ui_domain || (var.cognito_domain_prefix != null && length(var.cognito_domain_prefix) > 0)
    error_message = "cognito_domain_prefix must be set when create_hosted_ui_domain is true."
  }
}

# --- Human app client (Authorization Code + PKCE) -----------------------------

variable "human_callback_urls" {
  description = <<-EOT
    OAuth2 callback (redirect) URLs for the human app client. Required, no
    default — no frontend exists yet, so no real callback URL exists
    either. A real terraform plan/apply must not proceed with invented
    values; supply the actual, approved URL(s) when a client exists.
    Rejected outright: any URL containing "localhost" or "example.com" —
    those are not valid production values, only ever appropriate in this
    module's own offline tests.
  EOT
  type        = list(string)

  validation {
    condition     = alltrue([for u in var.human_callback_urls : !strcontains(lower(u), "localhost") && !strcontains(lower(u), "example.com")])
    error_message = "human_callback_urls must not contain localhost or example.com — those are not valid production values."
  }
}

variable "human_logout_urls" {
  description = "OAuth2 logout redirect URLs for the human app client. Required, no default — same reasoning as human_callback_urls."
  type        = list(string)

  validation {
    condition     = alltrue([for u in var.human_logout_urls : !strcontains(lower(u), "localhost") && !strcontains(lower(u), "example.com")])
    error_message = "human_logout_urls must not contain localhost or example.com — those are not valid production values."
  }
}

# --- Token validity ------------------------------------------------------------

variable "access_token_validity_minutes" {
  description = "Access token validity, in minutes, for both app clients. Default 60."
  type        = number
  default     = 60
}

variable "id_token_validity_minutes" {
  description = "ID token validity, in minutes, for the human app client (M2M client_credentials tokens carry no ID token)."
  type        = number
  default     = 60
}

variable "refresh_token_validity_days" {
  description = <<-EOT
    Refresh token validity, in days, for the human app client. Default 30.
    Not used by the M2M client — client_credentials is not a
    refresh-token-based grant; a new access token is requested directly
    with the client credentials each time one is needed.
  EOT
  type        = number
  default     = 30
}

# --- Client-ID allowlist publication (design, TECH-130 §11) -------------------

variable "publish_client_ids_to_ssm" {
  description = <<-EOT
    Whether to publish the app client IDs (never secrets) as a CSV in an
    SSM Parameter Store String parameter, for a future ECS task definition
    to consume as SIPSA_JWT_ALLOWED_CLIENT_IDS (see
    infrastructure.config.security.AllowedClientIdsValidator — client IDs
    are identifiers, not secrets, confirmed by that class's own Javadoc).
    Default true. This module does not wire the parameter into
    modules/ecs-task's task definition itself (that is a follow-up, not
    part of TECH-130's scope) — it only publishes the value at a known,
    stable parameter name for that future wiring to reference.
  EOT
  type        = bool
  default     = true
}
