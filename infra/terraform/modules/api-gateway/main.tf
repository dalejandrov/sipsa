locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # Full scope string as Cognito issues it in the token's "scope" claim
  # (e.g. "sipsa/ingestion.execute") — matches exactly what SecurityConfig's
  # own SCOPE_sipsa/... authorities already check, never invented.
  scope_execute = "${var.cognito_resource_server_identifier}/ingestion.execute"
  scope_cancel  = "${var.cognito_resource_server_identifier}/ingestion.cancel"
  scope_read    = "${var.cognito_resource_server_identifier}/ingestion.read"
  scope_audit   = "${var.cognito_resource_server_identifier}/audit.read"

  # Every /api/internal/** leaf route this application actually exposes
  # (inventoried via grep against src/main/java, not invented — see
  # README.md's endpoint table). Each entry's resource_id is filled in below
  # once the corresponding aws_api_gateway_resource exists, so this map can
  # drive a single for_each for the (otherwise near-identical) method +
  # integration pair. full_path is the exact Spring route this forwards to
  # (never rewritten); path_param (nullable) is the single {..} path
  # parameter name this route carries, if any, wired into both the method's
  # and the integration's request_parameters.
  internal_routes = {
    ingestion_run = {
      resource_id     = aws_api_gateway_resource.ingestion_run.id
      http_method     = "POST"
      scopes          = [local.scope_execute]
      strict_throttle = true
      full_path       = "/api/internal/ingestion/run"
      path_param      = null
    }
    ingestion_methods = {
      resource_id     = aws_api_gateway_resource.ingestion_methods.id
      http_method     = "GET"
      scopes          = [local.scope_read]
      strict_throttle = false
      full_path       = "/api/internal/ingestion/methods"
      path_param      = null
    }
    ingestion_cancel = {
      resource_id     = aws_api_gateway_resource.ingestion_cancel_run_id.id
      http_method     = "POST"
      scopes          = [local.scope_cancel]
      strict_throttle = true
      full_path       = "/api/internal/ingestion/cancel/{runId}"
      path_param      = "runId"
    }
    ingestion_running = {
      resource_id     = aws_api_gateway_resource.ingestion_running.id
      http_method     = "GET"
      scopes          = [local.scope_read]
      strict_throttle = false
      full_path       = "/api/internal/ingestion/running"
      path_param      = null
    }
    ingestion_runs = {
      resource_id     = aws_api_gateway_resource.ingestion_runs.id
      http_method     = "GET"
      scopes          = [local.scope_read]
      strict_throttle = false
      full_path       = "/api/internal/ingestion/runs"
      path_param      = null
    }
    ingestion_runs_by_id = {
      resource_id     = aws_api_gateway_resource.ingestion_runs_run_id.id
      http_method     = "GET"
      scopes          = [local.scope_read]
      strict_throttle = false
      full_path       = "/api/internal/ingestion/runs/{runId}"
      path_param      = "runId"
    }
    audit_request_by_id = {
      resource_id     = aws_api_gateway_resource.audit_request_request_id.id
      http_method     = "GET"
      scopes          = [local.scope_audit]
      strict_throttle = false
      full_path       = "/api/internal/audit/request/{requestId}"
      path_param      = "requestId"
    }
    audit_run_by_id = {
      resource_id     = aws_api_gateway_resource.audit_run_run_id.id
      http_method     = "GET"
      scopes          = [local.scope_audit]
      strict_throttle = false
      full_path       = "/api/internal/audit/run/{runId}"
      path_param      = "runId"
    }
    audit_recent = {
      resource_id     = aws_api_gateway_resource.audit_recent.id
      http_method     = "GET"
      scopes          = [local.scope_audit]
      strict_throttle = false
      full_path       = "/api/internal/audit/recent"
      path_param      = null
    }
    audit_all = {
      resource_id     = aws_api_gateway_resource.audit_all.id
      http_method     = "GET"
      scopes          = [local.scope_audit]
      strict_throttle = false
      full_path       = "/api/internal/audit/all"
      path_param      = null
    }
  }
}

# =============================================================================
# NLB fronting the VPC Link, chained to the existing internal ALB.
#
# aws_api_gateway_vpc_link (classic REST API VPC Link) accepts ONLY a Network
# Load Balancer ARN in target_arns — never an Application Load Balancer
# directly (confirmed against the provider's own resource docs, not assumed;
# ADR-010's original "VPC Link to the ALB" diagram undersimplified this).
# AWS supports registering an existing ALB as the single target of an NLB
# target group (target_type = "alb") — this is the standard, AWS-documented
# pattern for this exact situation, not a workaround invented here.
# =============================================================================

resource "aws_lb" "vpc_link" {
  name               = "${local.name_prefix}-vpc-link-nlb"
  internal           = true
  load_balancer_type = "network"
  subnets            = var.private_app_subnet_ids

  # No security_groups: this NLB exists solely to front the API Gateway VPC
  # Link (a private, non-internet-routable AWS mechanism) and forward to the
  # existing internal ALB — the meaningful access-control boundary is the
  # ALB's own security group (modules/ecs-service), not this pass-through
  # Layer-4 load balancer.
  enable_cross_zone_load_balancing = true

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-vpc-link-nlb"
  })
}

# target_type = "alb": AWS permits exactly one ALB per target group of this
# type; the target group's port must match the ALB listener's own port
# exactly (AWS requirement, not a default carried over from elsewhere).
resource "aws_lb_target_group" "alb_target" {
  name        = "${local.name_prefix}-vpc-link-tg"
  target_type = "alb"
  protocol    = "TCP"
  port        = var.alb_listener_port
  vpc_id      = var.vpc_id

  # Health checks are sent to the ALB and forwarded to its own targets —
  # this mirrors the ALB's own listener (HTTP/var.alb_listener_port) and
  # the same /actuator/health path already used at the ALB->ECS layer
  # (modules/ecs-service), not a new health-check contract.
  health_check {
    protocol            = "HTTP"
    port                = "traffic-port"
    path                = var.alb_health_check_path
    matcher             = "200"
    healthy_threshold   = 3
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 10
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-vpc-link-tg"
  })
}

resource "aws_lb_target_group_attachment" "alb_target" {
  target_group_arn = aws_lb_target_group.alb_target.arn
  target_id        = var.alb_arn
  port             = var.alb_listener_port
}

resource "aws_lb_listener" "vpc_link_tcp" {
  load_balancer_arn = aws_lb.vpc_link.arn
  port              = var.alb_listener_port
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.alb_target.arn
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-vpc-link-listener"
  })
}

resource "aws_api_gateway_vpc_link" "main" {
  name        = "${local.name_prefix}-vpc-link"
  description = "Private connection from API Gateway to the internal ALB, via an NLB target-group chain (REST API VPC Links accept only NLB targets)."
  target_arns = [aws_lb.vpc_link.arn]

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-vpc-link"
  })
}

# =============================================================================
# REST API + Cognito authorizer
# =============================================================================

resource "aws_api_gateway_rest_api" "main" {
  name = "${local.name_prefix}-api"

  endpoint_configuration {
    types = [var.endpoint_type]
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-api"
  })
}

resource "aws_api_gateway_authorizer" "cognito" {
  name          = "${local.name_prefix}-cognito-authorizer"
  rest_api_id   = aws_api_gateway_rest_api.main.id
  type          = "COGNITO_USER_POOLS"
  provider_arns = [var.cognito_user_pool_arn]
  # identity_source left at its default (method.request.header.Authorization)
  # — the standard Bearer-token header, not a custom one.
}

# =============================================================================
# /api/sipsa/** — public functional API (no Cognito authorizer; API key +
# usage plan only). A single {proxy+} resource forwards every sub-path
# (ciudad, mayoristas/mensual, parcial, mayoristas/semanal,
# abastecimientos/mensual) to the backend unchanged — Spring's own
# @RequestMapping already owns the real routing; API Gateway's job here is
# edge concerns (API key, throttling, logging), not re-declaring 5
# near-identical GET routes.
# =============================================================================

resource "aws_api_gateway_resource" "api" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_rest_api.main.root_resource_id
  path_part   = "api"
}

resource "aws_api_gateway_resource" "sipsa" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.api.id
  path_part   = "sipsa"
}

resource "aws_api_gateway_resource" "sipsa_proxy" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.sipsa.id
  path_part   = "{proxy+}"
}

resource "aws_api_gateway_method" "sipsa_root" {
  rest_api_id      = aws_api_gateway_rest_api.main.id
  resource_id      = aws_api_gateway_resource.sipsa.id
  http_method      = "GET"
  authorization    = "NONE"
  api_key_required = true
}

resource "aws_api_gateway_integration" "sipsa_root" {
  rest_api_id             = aws_api_gateway_rest_api.main.id
  resource_id             = aws_api_gateway_resource.sipsa.id
  http_method             = aws_api_gateway_method.sipsa_root.http_method
  type                    = "HTTP_PROXY"
  integration_http_method = "GET"
  connection_type         = "VPC_LINK"
  connection_id           = aws_api_gateway_vpc_link.main.id
  uri                     = "http://${aws_lb.vpc_link.dns_name}:${var.alb_listener_port}/api/sipsa"
}

resource "aws_api_gateway_method" "sipsa_proxy" {
  rest_api_id      = aws_api_gateway_rest_api.main.id
  resource_id      = aws_api_gateway_resource.sipsa_proxy.id
  http_method      = "GET"
  authorization    = "NONE"
  api_key_required = true

  request_parameters = {
    "method.request.path.proxy" = true
  }
}

resource "aws_api_gateway_integration" "sipsa_proxy" {
  rest_api_id             = aws_api_gateway_rest_api.main.id
  resource_id             = aws_api_gateway_resource.sipsa_proxy.id
  http_method             = aws_api_gateway_method.sipsa_proxy.http_method
  type                    = "HTTP_PROXY"
  integration_http_method = "GET"
  connection_type         = "VPC_LINK"
  connection_id           = aws_api_gateway_vpc_link.main.id
  uri                     = "http://${aws_lb.vpc_link.dns_name}:${var.alb_listener_port}/api/sipsa/{proxy}"

  request_parameters = {
    "integration.request.path.proxy" = "method.request.path.proxy"
  }
}

# CORS — disabled by default (var.cors_allowed_origins is empty; see its
# description in variables.tf: no browser-client requirement has been
# established anywhere in this repository). When exactly one origin is
# configured, a static Access-Control-Allow-Origin response header is added
# to both public GET methods — API Gateway's native (non-Lambda) response
# headers only support a fixed value, not a per-request dynamic Origin
# echo across multiple allowed origins (see the cors_allowed_origins
# variable's own validation, capping it at one entry for exactly this
# reason). Access-Control-Allow-Credentials is included only when
# var.cors_allow_credentials is true — never paired with a wildcard origin
# (validated on cors_allow_credentials itself).
locals {
  cors_enabled = length(var.cors_allowed_origins) == 1

  cors_response_parameters = local.cors_enabled ? merge(
    { "method.response.header.Access-Control-Allow-Origin" = true },
    var.cors_allow_credentials ? { "method.response.header.Access-Control-Allow-Credentials" = true } : {}
  ) : {}

  cors_integration_response_parameters = local.cors_enabled ? merge(
    { "method.response.header.Access-Control-Allow-Origin" = "'${try(var.cors_allowed_origins[0], "")}'" },
    var.cors_allow_credentials ? { "method.response.header.Access-Control-Allow-Credentials" = "'true'" } : {}
  ) : {}
}

resource "aws_api_gateway_method_response" "sipsa_root_cors" {
  count = local.cors_enabled ? 1 : 0

  rest_api_id         = aws_api_gateway_rest_api.main.id
  resource_id         = aws_api_gateway_resource.sipsa.id
  http_method         = aws_api_gateway_method.sipsa_root.http_method
  status_code         = "200"
  response_parameters = local.cors_response_parameters
}

resource "aws_api_gateway_integration_response" "sipsa_root_cors" {
  count = local.cors_enabled ? 1 : 0

  rest_api_id         = aws_api_gateway_rest_api.main.id
  resource_id         = aws_api_gateway_resource.sipsa.id
  http_method         = aws_api_gateway_method.sipsa_root.http_method
  status_code         = aws_api_gateway_method_response.sipsa_root_cors[0].status_code
  response_parameters = local.cors_integration_response_parameters

  depends_on = [aws_api_gateway_integration.sipsa_root]
}

resource "aws_api_gateway_method_response" "sipsa_proxy_cors" {
  count = local.cors_enabled ? 1 : 0

  rest_api_id         = aws_api_gateway_rest_api.main.id
  resource_id         = aws_api_gateway_resource.sipsa_proxy.id
  http_method         = aws_api_gateway_method.sipsa_proxy.http_method
  status_code         = "200"
  response_parameters = local.cors_response_parameters
}

resource "aws_api_gateway_integration_response" "sipsa_proxy_cors" {
  count = local.cors_enabled ? 1 : 0

  rest_api_id         = aws_api_gateway_rest_api.main.id
  resource_id         = aws_api_gateway_resource.sipsa_proxy.id
  http_method         = aws_api_gateway_method.sipsa_proxy.http_method
  status_code         = aws_api_gateway_method_response.sipsa_proxy_cors[0].status_code
  response_parameters = local.cors_integration_response_parameters

  depends_on = [aws_api_gateway_integration.sipsa_proxy]
}

# =============================================================================
# /api/internal/** — Cognito-authorized, per-route scope, no API key
# (ADR-010: "no API key requirement on admin routes" — the Cognito token
# itself is the identification+authorization mechanism here, not a key).
# =============================================================================

resource "aws_api_gateway_resource" "internal" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.api.id
  path_part   = "internal"
}

# --- ingestion ---

resource "aws_api_gateway_resource" "ingestion" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.internal.id
  path_part   = "ingestion"
}

resource "aws_api_gateway_resource" "ingestion_run" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.ingestion.id
  path_part   = "run"
}

resource "aws_api_gateway_resource" "ingestion_methods" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.ingestion.id
  path_part   = "methods"
}

resource "aws_api_gateway_resource" "ingestion_cancel" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.ingestion.id
  path_part   = "cancel"
}

resource "aws_api_gateway_resource" "ingestion_cancel_run_id" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.ingestion_cancel.id
  path_part   = "{runId}"
}

resource "aws_api_gateway_resource" "ingestion_running" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.ingestion.id
  path_part   = "running"
}

resource "aws_api_gateway_resource" "ingestion_runs" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.ingestion.id
  path_part   = "runs"
}

resource "aws_api_gateway_resource" "ingestion_runs_run_id" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.ingestion_runs.id
  path_part   = "{runId}"
}

# --- audit ---

resource "aws_api_gateway_resource" "audit" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.internal.id
  path_part   = "audit"
}

resource "aws_api_gateway_resource" "audit_request" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.audit.id
  path_part   = "request"
}

resource "aws_api_gateway_resource" "audit_request_request_id" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.audit_request.id
  path_part   = "{requestId}"
}

resource "aws_api_gateway_resource" "audit_run" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.audit.id
  path_part   = "run"
}

resource "aws_api_gateway_resource" "audit_run_run_id" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.audit_run.id
  path_part   = "{runId}"
}

resource "aws_api_gateway_resource" "audit_recent" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.audit.id
  path_part   = "recent"
}

resource "aws_api_gateway_resource" "audit_all" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_resource.audit.id
  path_part   = "all"
}

# One method + one HTTP_PROXY integration per real /api/internal/** leaf
# route (local.internal_routes, inventoried by grep — see README.md).
# authorization_scopes enforces the token carries the specific scope this
# operation needs — the SAME per-operation precision SecurityConfig already
# enforces as defense-in-depth (ADR-002 §3: four independent layers, not one
# substituting for another).
resource "aws_api_gateway_method" "internal" {
  for_each = local.internal_routes

  rest_api_id          = aws_api_gateway_rest_api.main.id
  resource_id          = each.value.resource_id
  http_method          = each.value.http_method
  authorization        = "COGNITO_USER_POOLS"
  authorizer_id        = aws_api_gateway_authorizer.cognito.id
  authorization_scopes = each.value.scopes
  api_key_required     = false

  request_parameters = each.value.path_param == null ? {} : {
    "method.request.path.${each.value.path_param}" = true
  }
}

resource "aws_api_gateway_integration" "internal" {
  for_each = local.internal_routes

  rest_api_id             = aws_api_gateway_rest_api.main.id
  resource_id             = each.value.resource_id
  http_method             = aws_api_gateway_method.internal[each.key].http_method
  type                    = "HTTP_PROXY"
  integration_http_method = each.value.http_method
  connection_type         = "VPC_LINK"
  connection_id           = aws_api_gateway_vpc_link.main.id
  # The forwarded path is exactly the real Spring route (each.value.full_path
  # from local.internal_routes) — never rewritten or reconstructed.
  uri = "http://${aws_lb.vpc_link.dns_name}:${var.alb_listener_port}${each.value.full_path}"

  request_parameters = each.value.path_param == null ? {} : {
    "integration.request.path.${each.value.path_param}" = "method.request.path.${each.value.path_param}"
  }
}

# =============================================================================
# Deployment + stage
# =============================================================================

# redeployed whenever this module's own resource/method/integration
# definitions change — a content hash of main.tf itself, not a manually
# bumped counter (avoids forgetting to redeploy after editing a route).
resource "aws_api_gateway_deployment" "main" {
  rest_api_id = aws_api_gateway_rest_api.main.id

  triggers = {
    redeployment = filesha1("${path.module}/main.tf")
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [
    aws_api_gateway_integration.internal,
    aws_api_gateway_integration.sipsa_root,
    aws_api_gateway_integration.sipsa_proxy,
  ]
}

# Account-level CloudWatch role — a per-AWS-account/region singleton
# (aws_api_gateway_account has no rest_api_id; it configures the account,
# not this API specifically). Required for stage access logs and execution
# logs to actually deliver — API Gateway silently fails to write CloudWatch
# logs without this role, a well-documented operational prerequisite not
# stated as a hard Terraform-level requirement in the resource's own docs.
# Safe to create once per account (ADR-010: this repository owns a single,
# dedicated AWS account) — would need to be shared/imported, not
# re-declared, if a second API Gateway stack is ever added to this account.
resource "aws_iam_role" "apigateway_cloudwatch" {
  name = "${local.name_prefix}-apigateway-cloudwatch"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "apigateway.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = var.common_tags
}

resource "aws_iam_role_policy_attachment" "apigateway_cloudwatch" {
  role       = aws_iam_role.apigateway_cloudwatch.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
}

resource "aws_api_gateway_account" "main" {
  cloudwatch_role_arn = aws_iam_role.apigateway_cloudwatch.arn
}

# AWS-owned key (CloudWatch Logs' own default encryption) — same posture
# already applied to every other CloudWatch log group in this repository
# (modules/network, modules/database, modules/ecs-task); no compliance
# requirement for a dedicated key has been identified.
# trivy:ignore:AVD-AWS-0017
resource "aws_cloudwatch_log_group" "access_logs" {
  name              = "/aws/apigateway/${local.name_prefix}-access-logs"
  retention_in_days = var.access_log_retention_days

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-apigateway-access-logs"
  })
}

# X-Ray tracing left disabled: a real per-request cost this story does not
# adopt without an explicit cost decision (same posture as modules/ecs-task's
# Container Insights being the one accepted exception, justified by real
# operational need — X-Ray has no equivalent justification here yet).
# Revisit if request tracing across API Gateway -> VPC Link -> ALB -> ECS
# becomes a real operational need.
# trivy:ignore:AVD-AWS-0003
resource "aws_api_gateway_stage" "main" {
  rest_api_id   = aws_api_gateway_rest_api.main.id
  deployment_id = aws_api_gateway_deployment.main.id
  stage_name    = var.stage_name

  # Structured access logs — deliberately excludes anything from the
  # Authorization header, the API key value, or request/response bodies.
  # $context.requestId is API Gateway's OWN correlation ID, distinct from
  # the application's own ErrorResponse.requestId (TECH-023) — the two are
  # never the same value, since app-originated errors are the only ones
  # that ever reach the application's own request-ID generation.
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.access_logs.arn
    format = jsonencode({
      requestId             = "$context.requestId"
      extendedRequestId     = "$context.extendedRequestId"
      sourceIp              = "$context.identity.sourceIp"
      requestTime           = "$context.requestTime"
      httpMethod            = "$context.httpMethod"
      resourcePath          = "$context.resourcePath"
      status                = "$context.status"
      responseLength        = "$context.responseLength"
      integrationStatus     = "$context.integration.status"
      integrationLatency    = "$context.integration.latency"
      authorizerPrincipalId = "$context.authorizer.principalId"
      authorizerClaimsSub   = "$context.authorizer.claims.sub"
      apiKeyId              = "$context.identity.apiKeyId"
    })
  }

  xray_tracing_enabled = false

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-api-${var.stage_name}"
  })

  depends_on = [aws_api_gateway_account.main]
}

# Stage-wide default throttling (ADR-010 general tier) — every method not
# explicitly overridden below inherits this. Response caching deliberately
# not enabled: GET /api/internal/ingestion/runs, /running, and /runs/{runId}
# exist specifically to report LIVE run status — a cached stale answer to
# "is my ingestion done yet" is actively misleading, not just unhelpful.
# GET /api/sipsa/** could in principle tolerate caching once real traffic
# patterns exist, but that is a real requirement to establish first, not
# assume; caching also has a per-method cache-cluster cost this story does
# not adopt speculatively.
# trivy:ignore:AVD-AWS-0190
resource "aws_api_gateway_method_settings" "default" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  stage_name  = aws_api_gateway_stage.main.stage_name
  method_path = "*/*"

  settings {
    throttling_rate_limit  = var.general_rate_limit
    throttling_burst_limit = var.general_burst_limit
    metrics_enabled        = true
    logging_level          = "INFO"
    # Never true: data_trace_enabled logs full request/response payloads,
    # including the Authorization header and any request body — directly
    # contradicts "never log tokens/secrets/bodies".
    data_trace_enabled = false
  }
}

# Per-route override: the two ingestion-trigger routes get the stricter
# ADR-010 tier (1 req/s / burst 2), never the general default. Caching is
# never appropriate here regardless of traffic: these are mutating POST
# operations (trigger/cancel), and API Gateway does not cache non-GET
# methods in the first place.
# trivy:ignore:AVD-AWS-0190
resource "aws_api_gateway_method_settings" "ingestion_trigger" {
  for_each = { for k, v in local.internal_routes : k => v if v.strict_throttle }

  rest_api_id = aws_api_gateway_rest_api.main.id
  stage_name  = aws_api_gateway_stage.main.stage_name
  # AWS's documented method_path format: "{resource_path}/{http_method}",
  # no leading slash. Not empirically verified against a real deployed API
  # by this story (no terraform apply is ever run) — flagged explicitly in
  # README.md as a gap to confirm before real traffic.
  method_path = "${trimprefix(each.value.full_path, "/")}/${each.value.http_method}"

  settings {
    throttling_rate_limit  = var.ingestion_trigger_rate_limit
    throttling_burst_limit = var.ingestion_trigger_burst_limit
    metrics_enabled        = true
    logging_level          = "INFO"
    data_trace_enabled     = false
  }
}

# =============================================================================
# API keys + usage plan — /api/sipsa/** only (ADR-010: no API key
# requirement on /api/internal/** admin routes; the Cognito token is the
# identification mechanism there instead).
# =============================================================================

resource "aws_api_gateway_usage_plan" "general" {
  name        = "${local.name_prefix}-general-usage-plan"
  description = "Throttling/quota for API-key-bearing GET /api/sipsa/** consumers (ADR-010 general tier)."

  api_stages {
    api_id = aws_api_gateway_rest_api.main.id
    stage  = aws_api_gateway_stage.main.stage_name
  }

  throttle_settings {
    rate_limit  = var.general_rate_limit
    burst_limit = var.general_burst_limit
  }

  quota_settings {
    limit  = var.general_quota_limit
    period = "MONTH"
  }

  tags = var.common_tags
}

# One parameterizable API key per real consumer — one instantiated here
# (var.api_key_name), the same "one parameterizable client, not several
# speculative ones" precedent modules/cognito already established for the
# M2M app client. No `value` argument: AWS generates it. The generated
# value is schema-marked sensitive by the provider and is NEVER exposed as
# a module output (see outputs.tf) — only the key's ID.
resource "aws_api_gateway_api_key" "consumer" {
  name    = var.api_key_name
  enabled = true

  tags = var.common_tags
}

resource "aws_api_gateway_usage_plan_key" "consumer" {
  key_id        = aws_api_gateway_api_key.consumer.id
  key_type      = "API_KEY"
  usage_plan_id = aws_api_gateway_usage_plan.general.id
}

# =============================================================================
# Gateway responses — consistent JSON for errors that originate AT THE
# GATEWAY (never reach the application), distinct from the application's own
# GlobalExceptionHandler.ErrorResponse shape (timestamp/status/error/code/
# message/requestId/instance) used for errors the backend itself produces.
# Replicating that exact shape here would diverge the moment either side
# changes independently — not attempted. $context.requestId is API
# Gateway's own correlation ID, not the application's.
# =============================================================================

locals {
  gateway_response_template = jsonencode({
    status    = "$context.error.responseType"
    message   = "$context.error.message"
    requestId = "$context.requestId"
  })
}

resource "aws_api_gateway_gateway_response" "unauthorized" {
  rest_api_id   = aws_api_gateway_rest_api.main.id
  response_type = "UNAUTHORIZED"
  status_code   = "401"

  response_templates = {
    "application/json" = local.gateway_response_template
  }
}

resource "aws_api_gateway_gateway_response" "access_denied" {
  rest_api_id   = aws_api_gateway_rest_api.main.id
  response_type = "ACCESS_DENIED"
  status_code   = "403"

  response_templates = {
    "application/json" = local.gateway_response_template
  }
}

resource "aws_api_gateway_gateway_response" "throttled" {
  rest_api_id   = aws_api_gateway_rest_api.main.id
  response_type = "THROTTLED"
  status_code   = "429"

  response_templates = {
    "application/json" = local.gateway_response_template
  }
}

resource "aws_api_gateway_gateway_response" "default_5xx" {
  rest_api_id   = aws_api_gateway_rest_api.main.id
  response_type = "DEFAULT_5XX"

  response_templates = {
    "application/json" = local.gateway_response_template
  }
}
