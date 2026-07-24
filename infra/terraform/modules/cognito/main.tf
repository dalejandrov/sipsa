locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # The four scopes SecurityConfig already enforces
  # (src/main/java/.../infrastructure/config/security/SecurityConfig.java) —
  # confirmed by grep against the real code, not invented. Both the M2M and
  # human app clients are granted the full set: nothing in the application
  # restricts a given scope to a specific client type, only to the grant
  # itself proving the caller was authorized for it at token-issuance time.
  scopes = [
    { name = "ingestion.execute", description = "Trigger a SIPSA ingestion run" },
    { name = "ingestion.cancel", description = "Cancel a running or scheduled SIPSA ingestion run" },
    { name = "ingestion.read", description = "Read SIPSA ingestion run status and history" },
    { name = "audit.read", description = "Read the SIPSA ingestion audit trail" },
  ]

  resource_server_scopes = [for s in local.scopes : "${var.resource_server_identifier}/${s.name}"]
}

# ---------------------------------------------------------------------------
# User pool
# ---------------------------------------------------------------------------

resource "aws_cognito_user_pool" "main" {
  name = "${local.name_prefix}-users"

  # Sign in with an email address directly — no separate "username" concept
  # exists anywhere else in this system yet, so this is the simplest choice
  # rather than an invented username scheme.
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  username_configuration {
    case_sensitive = false
  }

  password_policy {
    minimum_length                   = var.password_minimum_length
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    require_uppercase                = true
    temporary_password_validity_days = 7
  }

  mfa_configuration = var.mfa_configuration

  # TOTP-based MFA only — no SMS configuration (which would need an
  # additional SNS IAM role and phone-number verification infrastructure
  # this story does not build). Required whenever mfa_configuration is not
  # "OFF" (Cognito rejects ON/OPTIONAL without at least one MFA method
  # configured).
  software_token_mfa_configuration {
    enabled = var.mfa_configuration != "OFF"
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  admin_create_user_config {
    allow_admin_create_user_only = var.allow_admin_create_user_only
  }

  user_pool_add_ons {
    advanced_security_mode = var.advanced_security_mode
  }

  deletion_protection = var.deletion_protection

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-users"
  })
}

# ---------------------------------------------------------------------------
# Hosted UI domain — optional, Cognito-managed prefix only (TECH-130 scope:
# no custom domain, no ACM, no Route 53). See variables.tf for why this
# defaults to false.
# ---------------------------------------------------------------------------

resource "aws_cognito_user_pool_domain" "hosted_ui" {
  count = var.create_hosted_ui_domain ? 1 : 0

  domain       = var.cognito_domain_prefix
  user_pool_id = aws_cognito_user_pool.main.id
}

# ---------------------------------------------------------------------------
# Resource server and scopes
# ---------------------------------------------------------------------------

resource "aws_cognito_resource_server" "sipsa" {
  identifier   = var.resource_server_identifier
  name         = var.resource_server_name
  user_pool_id = aws_cognito_user_pool.main.id

  dynamic "scope" {
    for_each = local.scopes

    content {
      scope_name        = scope.value.name
      scope_description = scope.value.description
    }
  }
}

# ---------------------------------------------------------------------------
# M2M app client — client_credentials only, confidential (has a secret).
# No authorization_code, no implicit, no user password grant.
# ---------------------------------------------------------------------------

resource "aws_cognito_user_pool_client" "m2m" {
  name         = "${local.name_prefix}-m2m"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = true

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_scopes                 = local.resource_server_scopes

  supported_identity_providers = ["COGNITO"]

  prevent_user_existence_errors = "ENABLED"
  enable_token_revocation       = true

  access_token_validity = var.access_token_validity_minutes

  token_validity_units {
    access_token = "minutes"
  }

  depends_on = [aws_cognito_resource_server.sipsa]
}

# ---------------------------------------------------------------------------
# Human app client — Authorization Code only, public (no secret). Cognito
# requires PKCE automatically for a public client on the authorization_code
# grant — there is no separate "require PKCE" argument on this resource;
# generate_secret = false is what makes this a public client, and Cognito
# enforces the PKCE code_challenge/code_verifier exchange for such clients
# by itself. No implicit flow, no password grant.
#
# TECH-143: gated behind var.enable_human_client (default false). No
# frontend exists yet, so no real callback/logout URL exists either — the
# human flow is declared (this resource's own config is fully real and
# ready) but not created in production until real URLs are approved. The
# M2M client above is entirely unaffected by this variable.
# ---------------------------------------------------------------------------

resource "aws_cognito_user_pool_client" "human" {
  count = var.enable_human_client ? 1 : 0

  name         = "${local.name_prefix}-human"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = concat(["openid", "email", "profile"], local.resource_server_scopes)

  callback_urls = var.human_callback_urls
  logout_urls   = var.human_logout_urls

  supported_identity_providers = ["COGNITO"]

  prevent_user_existence_errors = "ENABLED"
  enable_token_revocation       = true

  access_token_validity  = var.access_token_validity_minutes
  id_token_validity      = var.id_token_validity_minutes
  refresh_token_validity = var.refresh_token_validity_days

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  depends_on = [aws_cognito_resource_server.sipsa]
}

# ---------------------------------------------------------------------------
# M2M client secret — copied into Secrets Manager for controlled operational
# distribution, never exposed as a Terraform output. This does NOT remove
# the value from Terraform state: Cognito generates the secret, the AWS
# provider reads it back as a computed attribute during creation
# (`aws_cognito_user_pool_client.m2m.client_secret`, schema-marked
# `sensitive = true`, confirmed via `terraform providers schema`), and both
# this module's state and the root state that consumes it retain the raw
# value — `sensitive` only redacts CLI/log output, not what state stores.
# See README.md's "M2M client secret" section for the full state-exposure
# analysis and the backend controls (encryption, public-access block,
# locking, IAM role separation) that actually protect it. Also unlike some
# AWS secret-generation patterns (e.g. an IAM access key), Cognito's client
# secret is NOT a show-once value — it remains retrievable via the Cognito
# API after creation regardless of what this module does with it.
# AWS-owned key is acceptable at this stage: single-owner secret, no
# cross-account access, no compliance requirement yet mandating a CMK.
# Revisit with a customer-managed KMS key if a real access-control boundary
# (e.g. a partner team needing scoped decrypt) requires one — same posture
# already applied to bootstrap's S3 bucket, database's CloudWatch log
# groups, ecr's repository, and network's Flow Logs group this session.
# trivy:ignore:AVD-AWS-0098
resource "aws_secretsmanager_secret" "m2m_client_secret" {
  name        = "${local.name_prefix}-cognito-m2m-client-secret"
  description = "Cognito M2M app client credentials (client_id + client_secret) for the SIPSA resource server."

  tags = var.common_tags
}

resource "aws_secretsmanager_secret_version" "m2m_client_secret" {
  secret_id = aws_secretsmanager_secret.m2m_client_secret.id

  secret_string = jsonencode({
    client_id     = aws_cognito_user_pool_client.m2m.id
    client_secret = aws_cognito_user_pool_client.m2m.client_secret
  })
}

# ---------------------------------------------------------------------------
# Client-ID allowlist publication (identifiers only, never secrets) — see
# variables.tf for why this exists and what it does not do (wire into ECS).
# ---------------------------------------------------------------------------

resource "aws_ssm_parameter" "allowed_client_ids" {
  count = var.publish_client_ids_to_ssm ? 1 : 0

  name        = "/${local.name_prefix}/sipsa/jwt-allowed-client-ids"
  type        = "String"
  description = "CSV of Cognito app client IDs allowed to call /api/internal/** (SIPSA_JWT_ALLOWED_CLIENT_IDS). Identifiers, not secrets."
  # The human client only contributes an ID here when it actually exists
  # (var.enable_human_client) — never a phantom ID for a client that was
  # never created.
  value = join(",", concat(
    [aws_cognito_user_pool_client.m2m.id],
    var.enable_human_client ? [aws_cognito_user_pool_client.human[0].id] : []
  ))

  tags = var.common_tags
}
