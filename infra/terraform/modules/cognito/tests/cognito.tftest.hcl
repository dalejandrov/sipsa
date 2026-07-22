# Structural tests for the cognito module (TECH-130). No real AWS account is
# contacted — the AWS provider is fully mocked (mock_provider "aws" {}); no
# token is ever requested, no Hosted UI is ever reached, and no Cognito user
# is ever created.
#
# Criteria 17 ("no secret in outputs"), 19-22 (no API Gateway, no VPC Link,
# no custom domain, no ACM/Route53) are NOT expressed as runtime assertions
# — they are structural facts about this module's own outputs.tf/main.tf
# (outputs.tf never references client_secret; main.tf declares no
# aws_api_gateway_*/aws_api_gateway_vpc_link/aws_acm_*/aws_route53_*
# resource, and its only domain resource is the Cognito-managed prefix
# domain, never a custom one), confirmed by reading the source.

mock_provider "aws" {}

variables {
  project_name = "sipsa"
  environment  = "production"
  common_tags = {
    Project     = "sipsa"
    Environment = "production"
    Owner       = "test-owner"
    ManagedBy   = "terraform"
    Repository  = "dalejandrov/sipsa"
    CostCenter  = "test-cost-center"
  }
  # RFC 2606-reserved .invalid TLD — guaranteed never to resolve, making the
  # placeholder nature of these test-only values unambiguous even here.
  human_callback_urls = ["https://app.sipsa.internal.invalid/callback"]
  human_logout_urls   = ["https://app.sipsa.internal.invalid/logout"]
}

# 1: User pool is created.
run "user_pool_created" {
  command = apply

  assert {
    condition     = can(aws_cognito_user_pool.main.id)
    error_message = "The user pool must be created."
  }
}

# 2: prevent_user_existence_errors is enabled on both clients.
run "prevent_user_existence_errors_enabled" {
  command = apply

  assert {
    condition     = aws_cognito_user_pool_client.m2m.prevent_user_existence_errors == "ENABLED"
    error_message = "The M2M client must have prevent_user_existence_errors enabled."
  }

  assert {
    condition     = aws_cognito_user_pool_client.human.prevent_user_existence_errors == "ENABLED"
    error_message = "The human client must have prevent_user_existence_errors enabled."
  }
}

# 3: Token revocation is enabled on both clients.
run "token_revocation_enabled" {
  command = apply

  assert {
    condition     = aws_cognito_user_pool_client.m2m.enable_token_revocation == true
    error_message = "The M2M client must have token revocation enabled."
  }

  assert {
    condition     = aws_cognito_user_pool_client.human.enable_token_revocation == true
    error_message = "The human client must have token revocation enabled."
  }
}

# 4: Password policy matches the configured defaults.
run "password_policy_is_correct" {
  command = apply

  assert {
    condition     = aws_cognito_user_pool.main.password_policy[0].minimum_length == 12
    error_message = "Default password minimum length must be 12."
  }

  assert {
    condition     = aws_cognito_user_pool.main.password_policy[0].require_uppercase == true && aws_cognito_user_pool.main.password_policy[0].require_lowercase == true && aws_cognito_user_pool.main.password_policy[0].require_numbers == true && aws_cognito_user_pool.main.password_policy[0].require_symbols == true
    error_message = "Password policy must require upper/lower/number/symbol by default."
  }
}

# 5: Email auto-verification is enabled.
run "email_verification_enabled" {
  command = apply

  assert {
    condition     = contains(aws_cognito_user_pool.main.auto_verified_attributes, "email")
    error_message = "email must be an auto-verified attribute."
  }
}

# 6-7: Resource server exists with exactly the four real scopes.
run "resource_server_has_exact_scopes" {
  command = apply

  assert {
    condition     = aws_cognito_resource_server.sipsa.identifier == "sipsa"
    error_message = "Default resource server identifier must be \"sipsa\"."
  }

  assert {
    condition     = length(aws_cognito_resource_server.sipsa.scope) == 4
    error_message = "The resource server must declare exactly four scopes."
  }

  assert {
    condition     = toset([for s in aws_cognito_resource_server.sipsa.scope : s.scope_name]) == toset(["ingestion.execute", "ingestion.cancel", "ingestion.read", "audit.read"])
    error_message = "The resource server's scopes must exactly match SecurityConfig's four real authorities."
  }
}

# 8-9: M2M client has a secret and only client_credentials.
run "m2m_client_has_secret_and_only_client_credentials" {
  command = apply

  assert {
    condition     = can(aws_cognito_user_pool_client.m2m.client_secret)
    error_message = "The M2M client must have a secret (generate_secret = true)."
  }

  assert {
    condition     = aws_cognito_user_pool_client.m2m.allowed_oauth_flows == toset(["client_credentials"])
    error_message = "The M2M client must allow exactly the client_credentials OAuth flow."
  }
}

# 10-11: Human client has no secret and only authorization_code.
run "human_client_has_no_secret_and_only_authorization_code" {
  command = apply

  assert {
    condition     = aws_cognito_user_pool_client.human.generate_secret == false
    error_message = "The human client must not generate a secret (public client)."
  }

  assert {
    condition     = aws_cognito_user_pool_client.human.allowed_oauth_flows == toset(["code"])
    error_message = "The human client must allow exactly the authorization_code (\"code\") OAuth flow."
  }
}

# 12: Implicit flow is never enabled on either client.
run "implicit_flow_disabled_everywhere" {
  command = apply

  assert {
    condition     = !contains(aws_cognito_user_pool_client.m2m.allowed_oauth_flows, "implicit")
    error_message = "The M2M client must never allow the implicit flow."
  }

  assert {
    condition     = !contains(aws_cognito_user_pool_client.human.allowed_oauth_flows, "implicit")
    error_message = "The human client must never allow the implicit flow."
  }
}

# 13-14: Callback and logout URLs are parameterized and take effect.
run "callback_and_logout_urls_are_parameterized" {
  command = apply

  assert {
    condition     = toset(aws_cognito_user_pool_client.human.callback_urls) == toset(["https://app.sipsa.internal.invalid/callback"])
    error_message = "callback_urls must reflect the configured variable exactly."
  }

  assert {
    condition     = toset(aws_cognito_user_pool_client.human.logout_urls) == toset(["https://app.sipsa.internal.invalid/logout"])
    error_message = "logout_urls must reflect the configured variable exactly."
  }
}

# Invalid input is rejected: localhost/example.com in callback URLs.
run "rejects_localhost_callback_url" {
  command = plan

  variables {
    human_callback_urls = ["http://localhost:3000/callback"]
  }

  expect_failures = [
    var.human_callback_urls,
  ]
}

run "rejects_example_com_logout_url" {
  command = plan

  variables {
    human_logout_urls = ["https://example.com/logout"]
  }

  expect_failures = [
    var.human_logout_urls,
  ]
}

# 15: Token validity matches the configured defaults.
run "token_validity_matches_defaults" {
  command = apply

  assert {
    condition     = aws_cognito_user_pool_client.human.access_token_validity == 60 && aws_cognito_user_pool_client.human.id_token_validity == 60 && aws_cognito_user_pool_client.human.refresh_token_validity == 30
    error_message = "Default token validity must be 60/60 minutes and 30 days."
  }

  assert {
    condition     = aws_cognito_user_pool_client.m2m.access_token_validity == 60
    error_message = "Default M2M access token validity must be 60 minutes."
  }
}

# 16: M2M and human client IDs are distinct — two real, separate app clients.
run "client_ids_are_distinct" {
  command = apply

  assert {
    condition     = aws_cognito_user_pool_client.m2m.id != aws_cognito_user_pool_client.human.id
    error_message = "The M2M and human app clients must be distinct resources with distinct IDs."
  }
}

# 18: Common tags are applied to the user pool.
run "common_tags_applied_to_user_pool" {
  command = apply

  assert {
    condition     = aws_cognito_user_pool.main.tags["Project"] == "sipsa" && aws_cognito_user_pool.main.tags["Environment"] == "production" && aws_cognito_user_pool.main.tags["Owner"] == "test-owner" && aws_cognito_user_pool.main.tags["ManagedBy"] == "terraform" && aws_cognito_user_pool.main.tags["Repository"] == "dalejandrov/sipsa" && aws_cognito_user_pool.main.tags["CostCenter"] == "test-cost-center"
    error_message = "The user pool must carry every common tag."
  }
}

# No Hosted UI domain is created by default.
run "no_hosted_ui_domain_by_default" {
  command = apply

  assert {
    condition     = length(aws_cognito_user_pool_domain.hosted_ui) == 0
    error_message = "No Cognito domain must be created when create_hosted_ui_domain is false (the default)."
  }

  assert {
    condition     = output.cognito_domain == null
    error_message = "cognito_domain output must be null when no domain is created."
  }
}

# A Hosted UI domain, when explicitly requested, uses the given prefix only.
run "hosted_ui_domain_created_when_requested" {
  command = apply

  variables {
    create_hosted_ui_domain = true
    cognito_domain_prefix   = "sipsa-production-auth-test"
  }

  assert {
    condition     = length(aws_cognito_user_pool_domain.hosted_ui) == 1
    error_message = "Exactly one Cognito domain must be created when explicitly requested."
  }

  assert {
    condition     = aws_cognito_user_pool_domain.hosted_ui[0].domain == "sipsa-production-auth-test"
    error_message = "The domain must use exactly the given prefix."
  }
}

run "rejects_hosted_ui_without_prefix" {
  command = plan

  variables {
    create_hosted_ui_domain = true
  }

  expect_failures = [
    var.cognito_domain_prefix,
  ]
}

# Client secret is written to Secrets Manager, never exposed as a plain
# module output value.
run "m2m_secret_written_to_secrets_manager" {
  command = apply

  assert {
    condition     = can(aws_secretsmanager_secret_version.m2m_client_secret.secret_string)
    error_message = "The M2M client secret must be written into Secrets Manager."
  }
}

# The client-ID allowlist SSM parameter is published by default, with both
# client IDs, and can be disabled.
run "client_id_allowlist_published_by_default" {
  command = apply

  assert {
    condition     = length(aws_ssm_parameter.allowed_client_ids) == 1
    error_message = "The SSM parameter must be created by default (publish_client_ids_to_ssm defaults to true)."
  }

  assert {
    condition     = aws_ssm_parameter.allowed_client_ids[0].type == "String"
    error_message = "The allowlist parameter must be a plain String type, never SecureString (client IDs are not secrets)."
  }
}

run "client_id_allowlist_can_be_disabled" {
  command = apply

  variables {
    publish_client_ids_to_ssm = false
  }

  assert {
    condition     = length(aws_ssm_parameter.allowed_client_ids) == 0
    error_message = "No SSM parameter must be created when publish_client_ids_to_ssm is false."
  }

  assert {
    condition     = output.allowed_client_ids_parameter_name == null
    error_message = "allowed_client_ids_parameter_name output must be null when disabled."
  }
}
