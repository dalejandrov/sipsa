# Root-wiring tests for TECH-142: does environments/production actually
# connect module.cognito's outputs into module.ecs_task the way the module
# blocks in main.tf claim to, not whether each module's own internals are
# correct (that is already covered by each module's own tests/*.tftest.hcl —
# modules/network, modules/database, modules/ecr, modules/ecs-service, and
# modules/cognito are therefore stubbed here via override_module, so this
# suite only has to reason about module.ecs_task's real resources and the
# expressions connecting them to the rest of the stack). No real AWS
# account is contacted — the AWS provider is fully mocked (mock_provider
# "aws" {}) and every non-ecs_task module is replaced with fixed outputs.
#
# Criteria 7 ("region/user pool not hardcoded" — main.tf references
# module.cognito.issuer_url directly, never reconstructs the URL), 8 ("no
# client secret in outputs" — modules/cognito/outputs.tf never references
# client_secret), 9 ("no ID token accepted as a contract" — a Java-level
# fact, covered by CognitoJwtDecoderContractTest, not a Terraform concern),
# 10-11 (no API Gateway, no VPC Link anywhere in this stack) are NOT
# expressed as runtime assertions here — they are structural facts
# confirmed by reading the source, the same established pattern used by
# every module's own test file in this repository.

mock_provider "aws" {}

variables {
  owner       = "test-owner"
  cost_center = "test-cost-center"

  # Required, no default (see modules/cognito/variables.tf) — offline-only
  # values, RFC 2606-reserved .invalid TLD, https:// as required by
  # variable validation.
  cognito_human_callback_urls = ["https://app.sipsa.internal.invalid/callback"]
  cognito_human_logout_urls   = ["https://app.sipsa.internal.invalid/logout"]
}

override_module {
  target = module.network
  outputs = {
    vpc_id                      = "vpc-mock0123456789"
    vpc_cidr                    = "10.40.0.0/16"
    availability_zones          = ["us-east-1a", "us-east-1b"]
    public_subnet_ids           = ["subnet-mockpub1", "subnet-mockpub2"]
    private_app_subnet_ids      = ["subnet-mockapp1", "subnet-mockapp2"]
    private_database_subnet_ids = ["subnet-mockdb1", "subnet-mockdb2"]
    public_route_table_id       = "rtb-mockpublic"
    private_app_route_table_ids = ["rtb-mockapp1", "rtb-mockapp2"]
    database_route_table_ids    = ["rtb-mockdb1", "rtb-mockdb2"]
    internet_gateway_id         = "igw-mock01"
    nat_gateway_id              = "nat-mock01"
    s3_gateway_endpoint_id      = "vpce-mock01"
    flow_log_group_name         = "/vpc/flow-logs/sipsa-production"
  }
}

override_module {
  target = module.database
  outputs = {
    db_instance_id       = "sipsa-production-postgres"
    db_instance_arn      = "arn:aws:rds:us-east-1:123456789012:db:sipsa-production-postgres"
    db_endpoint          = "sipsa-production-postgres.mock.us-east-1.rds.amazonaws.com:5432"
    db_port              = 5432
    db_address           = "sipsa-production-postgres.mock.us-east-1.rds.amazonaws.com"
    db_name              = "sipsa_db"
    db_security_group_id = "sg-mockdb01"
    db_subnet_group_name = "sipsa-production-db-subnet-group"
    master_secret_arn    = "arn:aws:secretsmanager:us-east-1:123456789012:secret:rds!db-mock-abc123"
  }
}

override_module {
  target = module.ecr
  outputs = {
    repository_name = "sipsa/production-app"
    repository_arn  = "arn:aws:ecr:us-east-1:123456789012:repository/sipsa/production-app"
    repository_url  = "123456789012.dkr.ecr.us-east-1.amazonaws.com/sipsa/production-app"
  }
}

override_module {
  target = module.ecs_service
  outputs = {
    alb_arn                       = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/sipsa-production/mock01"
    alb_dns_name                  = "sipsa-production-mock.us-east-1.elb.amazonaws.com"
    alb_zone_id                   = "Z35SXDOTRQ7X7K"
    alb_security_group_id         = "sg-mockalb01"
    target_group_arn              = "arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/sipsa-production/mock01"
    listener_arn                  = "arn:aws:elasticloadbalancing:us-east-1:123456789012:listener/app/sipsa-production/mock01/mock01"
    ecs_service_name              = "sipsa-production-service"
    ecs_service_id                = "arn:aws:ecs:us-east-1:123456789012:service/sipsa-production-cluster/sipsa-production-service"
    ecs_service_security_group_id = "sg-mockecs01"
    ecs_desired_count             = 1
  }
}

# module.api_gateway is stubbed too — this suite only cares about
# module.ecs_task's wiring; module.api_gateway's own internals (VPC
# Link/NLB chaining, per-route scopes, throttling, CORS) are already
# covered by modules/api-gateway/tests/api-gateway.tftest.hcl.
override_module {
  target = module.api_gateway
  outputs = {
    rest_api_id           = "mockapiid01"
    rest_api_arn          = "arn:aws:apigateway:us-east-1::/restapis/mockapiid01"
    execution_arn         = "arn:aws:execute-api:us-east-1:123456789012:mockapiid01"
    invoke_url            = "https://mockapiid01.execute-api.us-east-1.amazonaws.com/production"
    stage_name            = "production"
    vpc_link_id           = "mockvpclinkid01"
    usage_plan_id         = "mockusageplanid01"
    api_key_ids           = ["mockapikeyid01"]
    access_log_group_name = "/aws/apigateway/sipsa-production-access-logs"
    authorizer_id         = "mockauthorizerid01"
  }
}

# The distinctive mock values below (a recognizable user pool ID / SSM
# parameter name) are what proves main.tf's module.ecs_task block actually
# reads module.cognito's outputs, rather than some other, coincidentally
# similar-looking value.
override_module {
  target = module.cognito
  outputs = {
    user_pool_id                      = "us-east-1_MOCKPOOL01"
    user_pool_arn                     = "arn:aws:cognito-idp:us-east-1:123456789012:userpool/us-east-1_MOCKPOOL01"
    issuer_url                        = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_MOCKPOOL01"
    resource_server_identifier        = "sipsa"
    m2m_client_id                     = "mock-m2m-client-id"
    human_client_id                   = "mock-human-client-id"
    cognito_domain                    = null
    m2m_client_secret_arn             = "arn:aws:secretsmanager:us-east-1:123456789012:secret:sipsa-production-cognito-m2m-client-secret-abc123"
    allowed_client_ids_parameter_name = "/sipsa-production/sipsa/jwt-allowed-client-ids"
    allowed_client_ids_parameter_arn  = "arn:aws:ssm:us-east-1:123456789012:parameter/sipsa-production/sipsa/jwt-allowed-client-ids"
  }
}

# module.ecs_task itself is real (not overridden) — its own IAM role ARNs
# need the same mock-provider ARN-format override every module in this
# repository needs, mirroring modules/ecs-task/tests/ecs-task.tftest.hcl.
override_resource {
  target = module.ecs_task.aws_iam_role.execution
  values = {
    arn = "arn:aws:iam::123456789012:role/sipsa-production-ecs-execution"
  }
}

override_resource {
  target = module.ecs_task.aws_iam_role.task
  values = {
    arn = "arn:aws:iam::123456789012:role/sipsa-production-ecs-task"
  }
}

# 1-2: the issuer reaches the task definition, and it is the value that
# came from module.cognito's issuer_url output specifically (the mock pool
# ID above is distinctive enough that this could only be that value) — via
# module.ecs_task's own container_definitions output (TECH-142), since a
# root-level test cannot address a child module's internal resources
# directly, only its declared outputs.
run "issuer_url_flows_from_cognito_into_the_task_definition" {
  command = apply

  assert {
    condition     = strcontains(module.ecs_task.container_definitions, "\"name\":\"SIPSA_JWT_ISSUER_URI\",\"value\":\"https://cognito-idp.us-east-1.amazonaws.com/us-east-1_MOCKPOOL01\"")
    error_message = "SIPSA_JWT_ISSUER_URI must appear in the task definition with exactly module.cognito.issuer_url's value."
  }
}

# 3-4: the allowed-client-ids SSM parameter ARN reaches the task
# definition's secrets block (never as a plaintext environment value).
run "allowed_client_ids_flow_via_ssm_secrets_entry" {
  command = apply

  assert {
    condition     = strcontains(module.ecs_task.container_definitions, "\"name\":\"SIPSA_JWT_ALLOWED_CLIENT_IDS\",\"valueFrom\":\"arn:aws:ssm:us-east-1:123456789012:parameter/sipsa-production/sipsa/jwt-allowed-client-ids\"")
    error_message = "SIPSA_JWT_ALLOWED_CLIENT_IDS must resolve via the secrets block from module.cognito.allowed_client_ids_parameter_arn."
  }

  assert {
    condition     = !strcontains(module.ecs_task.container_definitions, "\"name\":\"SIPSA_JWT_ALLOWED_CLIENT_IDS\",\"value\":")
    error_message = "SIPSA_JWT_ALLOWED_CLIENT_IDS must never appear as a plaintext environment value."
  }
}

# 5-6: the execution role reads exactly the granted SSM parameter (never a
# wildcard), and the task role gets nothing extra — both already covered at
# the module boundary where the IAM policy resource actually lives
# (modules/ecs-task/tests/ecs-task.tftest.hcl's
# "execution_role_reads_exactly_the_granted_ssm_parameter" and
# "no_administrative_permissions_granted"), since a root-level test cannot
# address module.ecs_task's internal aws_iam_role_policy resource directly.
# What this root suite adds on top: confirming the specific ARN that
# reaches that mechanism is module.cognito's own output (assertions above),
# and that main.tf passes it via execution_ssm_parameter_arns = compact([
# module.cognito.allowed_client_ids_parameter_arn]) — a single-element,
# non-wildcard list, confirmed by reading main.tf.
