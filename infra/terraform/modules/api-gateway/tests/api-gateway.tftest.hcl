# Structural tests for the api-gateway module (TECH-131). No real AWS
# account is contacted — the AWS provider is fully mocked
# (mock_provider "aws" {}); `command = apply` never creates anything real,
# no VPC Link is ever reachable, no token is ever requested, no API key is
# ever retrieved.
#
# Criteria 22-25 (no custom domain, no Route53, no ACM, no WAF) are NOT
# expressed as runtime assertions — they are structural facts about this
# module's own main.tf (no aws_api_gateway_domain_name, aws_route53_*,
# aws_acm_*, or aws_wafv2_* resource is declared anywhere in it), confirmed
# by reading the source.

mock_provider "aws" {}

override_resource {
  target = aws_lb.vpc_link
  values = {
    arn      = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/net/sipsa-production-vpc-link-nlb/abc123"
    dns_name = "sipsa-production-vpc-link-nlb-abc123.elb.us-east-1.amazonaws.com"
  }
}

override_resource {
  target = aws_lb_target_group.alb_target
  values = {
    arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/sipsa-production-vpc-link-tg/abc123"
  }
}

override_resource {
  target = aws_iam_role.apigateway_cloudwatch
  values = {
    arn = "arn:aws:iam::123456789012:role/sipsa-production-apigateway-cloudwatch"
  }
}

override_resource {
  target = aws_cloudwatch_log_group.access_logs
  values = {
    arn = "arn:aws:logs:us-east-1:123456789012:log-group:/aws/apigateway/sipsa-production-access-logs:*"
  }
}

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

  vpc_id                 = "vpc-mock0123456789"
  private_app_subnet_ids = ["subnet-mockapp1", "subnet-mockapp2"]
  alb_arn                = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/sipsa-production-internal-alb/abc123"

  cognito_user_pool_arn              = "arn:aws:cognito-idp:us-east-1:123456789012:userpool/us-east-1_MOCKPOOL01"
  cognito_resource_server_identifier = "sipsa"
}

# 1-2: REST API, never HTTP API (HTTP API would be aws_apigatewayv2_*,
# never declared in this module — confirmed by inspection of main.tf, not
# a runtime-testable distinction between two resources of the same type).
run "rest_api_created" {
  command = apply

  assert {
    condition     = can(aws_api_gateway_rest_api.main.id)
    error_message = "The REST API must be created."
  }
}

# 3: VPC Link targets the NLB (never the ALB directly).
run "vpc_link_targets_the_nlb" {
  command = apply

  assert {
    condition     = toset(aws_api_gateway_vpc_link.main.target_arns) == toset([aws_lb.vpc_link.arn])
    error_message = "The VPC Link must target exactly the NLB's ARN."
  }
}

# 4: the NLB's target group targets the ALB (type = alb), and the ALB is
# its only target.
run "nlb_target_group_targets_the_alb" {
  command = apply

  assert {
    condition     = aws_lb_target_group.alb_target.target_type == "alb"
    error_message = "The NLB target group must be of type \"alb\"."
  }

  assert {
    condition     = aws_lb_target_group_attachment.alb_target.target_id == var.alb_arn
    error_message = "The NLB target group's single target must be the given ALB ARN."
  }

  assert {
    condition     = aws_lb_target_group.alb_target.port == var.alb_listener_port
    error_message = "The NLB target group's port must match the ALB listener's port exactly."
  }
}

# 5: Cognito authorizer wired to the given user pool.
run "cognito_authorizer_configured" {
  command = apply

  assert {
    condition     = aws_api_gateway_authorizer.cognito.type == "COGNITO_USER_POOLS"
    error_message = "The authorizer must be of type COGNITO_USER_POOLS."
  }

  assert {
    condition     = aws_api_gateway_authorizer.cognito.provider_arns == toset([var.cognito_user_pool_arn])
    error_message = "The authorizer must reference exactly the given Cognito user pool ARN."
  }
}

# 6: every /api/internal/** route requires the exact single scope it needs
# — never a shared, looser set.
run "internal_routes_require_exact_scopes" {
  command = apply

  assert {
    condition     = toset(aws_api_gateway_method.internal["ingestion_run"].authorization_scopes) == toset(["sipsa/ingestion.execute"])
    error_message = "POST run must require exactly sipsa/ingestion.execute."
  }

  assert {
    condition     = toset(aws_api_gateway_method.internal["ingestion_cancel"].authorization_scopes) == toset(["sipsa/ingestion.cancel"])
    error_message = "POST cancel/{runId} must require exactly sipsa/ingestion.cancel."
  }

  assert {
    condition     = toset(aws_api_gateway_method.internal["ingestion_runs"].authorization_scopes) == toset(["sipsa/ingestion.read"])
    error_message = "GET runs must require exactly sipsa/ingestion.read."
  }

  assert {
    condition     = toset(aws_api_gateway_method.internal["audit_recent"].authorization_scopes) == toset(["sipsa/audit.read"])
    error_message = "GET audit/recent must require exactly sipsa/audit.read."
  }
}

# 7: every /api/internal/** route is Cognito-authorized (never NONE), and
# never requires an API key (ADR-010: no API key requirement on admin
# routes).
run "internal_routes_never_require_api_key" {
  command = apply

  assert {
    condition = alltrue([
      for k, m in aws_api_gateway_method.internal : m.api_key_required == false
    ])
    error_message = "No /api/internal/** route may require an API key."
  }

  assert {
    condition = alltrue([
      for k, m in aws_api_gateway_method.internal : m.authorization == "COGNITO_USER_POOLS"
    ])
    error_message = "Every /api/internal/** route must be Cognito-authorized."
  }
}

# 8-9: /api/sipsa/** requires an API key and is never Cognito-authorized —
# the opposite contract from /api/internal/**.
run "sipsa_routes_require_api_key_never_cognito" {
  command = apply

  assert {
    condition     = aws_api_gateway_method.sipsa_root.api_key_required == true && aws_api_gateway_method.sipsa_proxy.api_key_required == true
    error_message = "GET /api/sipsa and its proxy sub-paths must require an API key."
  }

  assert {
    condition     = aws_api_gateway_method.sipsa_root.authorization == "NONE" && aws_api_gateway_method.sipsa_proxy.authorization == "NONE"
    error_message = "GET /api/sipsa/** must never require Cognito authorization — it is the public functional API."
  }
}

# 10: usage plan carries the ADR-010 general throttle/quota values.
run "usage_plan_matches_adr010_general_tier" {
  command = apply

  assert {
    condition     = aws_api_gateway_usage_plan.general.throttle_settings[0].rate_limit == 10 && aws_api_gateway_usage_plan.general.throttle_settings[0].burst_limit == 20
    error_message = "Default general throttling must be 10 req/s / burst 20."
  }
}

run "usage_plan_quota_matches_adr010" {
  command = apply

  assert {
    condition     = aws_api_gateway_usage_plan.general.quota_settings[0].limit == 100000 && aws_api_gateway_usage_plan.general.quota_settings[0].period == "MONTH"
    error_message = "Default quota must be 100000 requests per MONTH."
  }
}

# 11-13: ingestion-trigger routes carry the stricter 1/2 tier, distinct
# from the general 10/20 tier, applied to exactly the two mutating routes.
run "ingestion_trigger_routes_use_the_strict_tier" {
  command = apply

  assert {
    condition     = length(aws_api_gateway_method_settings.ingestion_trigger) == 2
    error_message = "Exactly two routes (run, cancel/{runId}) must carry the strict ingestion-trigger throttle tier."
  }

  assert {
    condition     = aws_api_gateway_method_settings.ingestion_trigger["ingestion_run"].settings[0].throttling_rate_limit == 1 && aws_api_gateway_method_settings.ingestion_trigger["ingestion_run"].settings[0].throttling_burst_limit == 2
    error_message = "Default ingestion-trigger throttling must be 1 req/s / burst 2."
  }
}

# 14: access logs are configured with a real destination and format.
run "access_logs_configured" {
  command = apply

  assert {
    condition     = can(aws_api_gateway_stage.main.access_log_settings[0].destination_arn)
    error_message = "The stage must have access logging configured."
  }
}

# 15: access log retention is set explicitly (30 days by default).
run "access_log_retention_is_30_days_by_default" {
  command = apply

  assert {
    condition     = aws_cloudwatch_log_group.access_logs.retention_in_days == 30
    error_message = "Default access log retention must be 30 days."
  }
}

# 16: no token/secret/body ever appears in the access log format string —
# confirmed structurally against the rendered format, not by parsing a
# real log line (none exists, nothing is ever applied).
run "access_log_format_never_includes_tokens_or_bodies" {
  command = apply

  assert {
    condition     = !strcontains(aws_api_gateway_stage.main.access_log_settings[0].format, "Authorization") && !strcontains(aws_api_gateway_stage.main.access_log_settings[0].format, "apiKeyValue")
    error_message = "The access log format must never reference the Authorization header or a raw API key value."
  }

  assert {
    condition     = aws_api_gateway_method_settings.default.settings[0].data_trace_enabled == false
    error_message = "data_trace_enabled must be false — it would log full request/response bodies, including tokens."
  }
}

# 17-20: gateway responses cover 401/403/429/5xx with a consistent,
# non-application-shaped body.
run "gateway_responses_cover_401_403_429_5xx" {
  command = apply

  assert {
    condition     = aws_api_gateway_gateway_response.unauthorized.status_code == "401"
    error_message = "UNAUTHORIZED must map to 401."
  }

  assert {
    condition     = aws_api_gateway_gateway_response.access_denied.status_code == "403"
    error_message = "ACCESS_DENIED must map to 403."
  }

  assert {
    condition     = aws_api_gateway_gateway_response.throttled.status_code == "429"
    error_message = "THROTTLED must map to 429."
  }

  assert {
    condition     = can(aws_api_gateway_gateway_response.default_5xx.id)
    error_message = "A DEFAULT_5XX gateway response must exist."
  }
}

# 21: stage is named "production".
run "stage_is_production" {
  command = apply

  assert {
    condition     = aws_api_gateway_stage.main.stage_name == "production"
    error_message = "Default stage name must be \"production\"."
  }
}

# 26: no API key value is ever exposed as a module output — confirmed
# structurally by outputs.tf never referencing aws_api_gateway_api_key's
# value attribute, and empirically here: only IDs are output.
run "api_key_output_never_exposes_the_value" {
  command = apply

  assert {
    condition     = length(output.api_key_ids) == 1 && output.api_key_ids[0] == aws_api_gateway_api_key.consumer.id
    error_message = "api_key_ids must expose exactly the key's ID, never its value."
  }
}

# 27-28: the ALB stays internal and receives no worldwide ingress — a fact
# about modules/ecs-service (already tested there); this module's own
# contribution is never opening the NLB/VPC Link to 0.0.0.0/0, confirmed
# structurally (no aws_security_group_rule with cidr_blocks = ["0.0.0.0/0"]
# is declared anywhere in this module's main.tf — it declares no security
# group at all, by design, see main.tf's own comment on aws_lb.vpc_link).

# 29: common tags applied to the REST API.
run "common_tags_applied_to_rest_api" {
  command = apply

  assert {
    condition     = aws_api_gateway_rest_api.main.tags["Project"] == "sipsa" && aws_api_gateway_rest_api.main.tags["Environment"] == "production" && aws_api_gateway_rest_api.main.tags["Owner"] == "test-owner" && aws_api_gateway_rest_api.main.tags["ManagedBy"] == "terraform" && aws_api_gateway_rest_api.main.tags["Repository"] == "dalejandrov/sipsa" && aws_api_gateway_rest_api.main.tags["CostCenter"] == "test-cost-center"
    error_message = "The REST API must carry every common tag."
  }
}

# CORS: disabled by default (no browser-client origin confirmed anywhere
# in this repository yet).
run "cors_disabled_by_default" {
  command = apply

  assert {
    condition     = length(aws_api_gateway_method_response.sipsa_root_cors) == 0 && length(aws_api_gateway_method_response.sipsa_proxy_cors) == 0
    error_message = "No CORS response headers must be configured when cors_allowed_origins is empty (the default)."
  }
}

# CORS: when a single real origin is configured, the response header
# reflects it exactly, and Access-Control-Allow-Credentials only appears
# when explicitly requested.
run "cors_enabled_with_single_origin" {
  command = apply

  variables {
    cors_allowed_origins   = ["https://app.sipsa.internal.invalid"]
    cors_allow_credentials = true
  }

  assert {
    condition     = aws_api_gateway_integration_response.sipsa_root_cors[0].response_parameters["method.response.header.Access-Control-Allow-Origin"] == "'https://app.sipsa.internal.invalid'"
    error_message = "The CORS response header must reflect exactly the configured origin."
  }

  assert {
    condition     = aws_api_gateway_integration_response.sipsa_root_cors[0].response_parameters["method.response.header.Access-Control-Allow-Credentials"] == "'true'"
    error_message = "Access-Control-Allow-Credentials must be set when cors_allow_credentials is true."
  }
}

# CORS: rejects more than one origin (this module's native, non-Lambda
# response header cannot dynamically echo per-request Origin across
# multiple allowed origins).
run "rejects_more_than_one_cors_origin" {
  command = plan

  variables {
    cors_allowed_origins = ["https://a.invalid", "https://b.invalid"]
  }

  expect_failures = [
    var.cors_allowed_origins,
  ]
}

# CORS: rejects a wildcard origin combined with credentials.
run "rejects_wildcard_origin_with_credentials" {
  command = plan

  variables {
    cors_allowed_origins   = ["*"]
    cors_allow_credentials = true
  }

  expect_failures = [
    var.cors_allow_credentials,
  ]
}
