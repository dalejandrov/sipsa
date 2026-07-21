# Structural tests for the network module (TECH-138). No real AWS account is
# contacted — the AWS provider is fully mocked (mock_provider "aws" {}
# below), so `command = apply` never creates anything real; it only lets
# Terraform's mock provider fully resolve computed attributes (IDs, ARNs)
# that stay unknown under `command = plan`.
#
# Run from infra/terraform/modules/network/ with: terraform test

mock_provider "aws" {}

override_data {
  target = data.aws_availability_zones.available
  values = {
    names = ["us-east-1a", "us-east-1b", "us-east-1c"]
  }
}

# The mock provider's default fake computed values for these two resources
# are not ARN-shaped strings, which fails aws_flow_log's own attribute
# validation for log_destination/iam_role_arn (it expects a real ARN format
# even under mocking). Supplying plausible-looking ARNs here is purely to
# satisfy that format check — no real AWS account is involved.
override_resource {
  target = aws_cloudwatch_log_group.flow_logs
  values = {
    arn = "arn:aws:logs:us-east-1:123456789012:log-group:/vpc-flow-logs/sipsa-production:*"
  }
}

override_resource {
  target = aws_iam_role.flow_logs
  values = {
    arn = "arn:aws:iam::123456789012:role/sipsa-production-vpc-flow-logs"
  }
}

variables {
  project_name = "sipsa"
  environment  = "production"
  aws_region   = "us-east-1"
  common_tags = {
    Project     = "sipsa"
    Environment = "production"
    Owner       = "test-owner"
    ManagedBy   = "terraform"
    Repository  = "dalejandrov/sipsa"
    CostCenter  = "test-cost-center"
  }
  vpc_cidr                      = "10.40.0.0/16"
  public_subnet_cidrs           = ["10.40.0.0/24", "10.40.1.0/24"]
  private_app_subnet_cidrs      = ["10.40.10.0/24", "10.40.11.0/24"]
  private_database_subnet_cidrs = ["10.40.20.0/24", "10.40.21.0/24"]
}

# 1-2: VPC exists, with DNS support and hostnames enabled.
run "vpc_has_dns_support_and_hostnames" {
  command = apply

  assert {
    condition     = aws_vpc.main.cidr_block == "10.40.0.0/16"
    error_message = "VPC CIDR must match the configured vpc_cidr variable."
  }

  assert {
    condition     = aws_vpc.main.enable_dns_support == true
    error_message = "VPC must have DNS support enabled."
  }

  assert {
    condition     = aws_vpc.main.enable_dns_hostnames == true
    error_message = "VPC must have DNS hostnames enabled."
  }
}

# 3: Exactly two Availability Zones are selected, deterministically.
run "selects_exactly_two_availability_zones" {
  command = apply

  assert {
    condition     = length(local.azs) == 2
    error_message = "Exactly two Availability Zones must be selected."
  }

  assert {
    condition     = local.azs[0] == "us-east-1a" && local.azs[1] == "us-east-1b"
    error_message = "AZ selection must be deterministic (first two from the available-AZs data source, in order) — never hardcoded to a specific literal AZ name in the resource config itself."
  }
}

# 4: Two public subnets, correct CIDRs, correct AZs.
run "public_subnets" {
  command = apply

  assert {
    condition     = length(aws_subnet.public) == 2
    error_message = "Exactly two public subnets must exist."
  }

  assert {
    condition     = aws_subnet.public[0].cidr_block == "10.40.0.0/24" && aws_subnet.public[1].cidr_block == "10.40.1.0/24"
    error_message = "Public subnet CIDRs must match the configured variable, in order."
  }
}

# 5: Two private application subnets.
run "private_application_subnets" {
  command = apply

  assert {
    condition     = length(aws_subnet.private_app) == 2
    error_message = "Exactly two private application subnets must exist."
  }

  assert {
    condition     = aws_subnet.private_app[0].cidr_block == "10.40.10.0/24" && aws_subnet.private_app[1].cidr_block == "10.40.11.0/24"
    error_message = "Private application subnet CIDRs must match the configured variable, in order."
  }
}

# 6: Two private database subnets.
run "private_database_subnets" {
  command = apply

  assert {
    condition     = length(aws_subnet.private_database) == 2
    error_message = "Exactly two private database subnets must exist."
  }

  assert {
    condition     = aws_subnet.private_database[0].cidr_block == "10.40.20.0/24" && aws_subnet.private_database[1].cidr_block == "10.40.21.0/24"
    error_message = "Private database subnet CIDRs must match the configured variable, in order."
  }
}

# 7: Exactly one NAT Gateway, in the first public subnet.
run "single_nat_gateway_in_first_public_subnet" {
  command = apply

  assert {
    condition     = can(aws_nat_gateway.main.id)
    error_message = "Exactly one NAT Gateway must exist (declared as a single, non-counted resource)."
  }

  assert {
    condition     = aws_nat_gateway.main.subnet_id == aws_subnet.public[0].id
    error_message = "The NAT Gateway must sit in the first public subnet."
  }
}

# 8: Private application route tables route through the NAT Gateway.
run "private_application_routes_to_nat" {
  command = apply

  assert {
    condition = alltrue([
      for rt in aws_route_table.private_app : anytrue([
        for r in rt.route : r.cidr_block == "0.0.0.0/0" && r.nat_gateway_id != ""
      ])
    ])
    error_message = "Every private application route table must have a default route through the NAT Gateway."
  }
}

# 9: Database route tables have no default route to the NAT Gateway or the IGW.
run "database_subnets_have_no_default_route" {
  command = apply

  assert {
    condition = alltrue([
      for rt in aws_route_table.database : length(rt.route) == 0
    ])
    error_message = "Database route tables must declare no route block at all — no NAT Gateway route, no Internet Gateway route."
  }
}

# 10: Public route table routes through the Internet Gateway.
run "public_subnets_route_to_internet_gateway" {
  command = apply

  assert {
    condition = anytrue([
      for r in aws_route_table.public.route : r.cidr_block == "0.0.0.0/0" && r.gateway_id != ""
    ])
    error_message = "The public route table must have a default route through the Internet Gateway."
  }
}

# 11: Private subnets (application and database) do not auto-assign public IPs.
run "private_subnets_have_no_public_ip_on_launch" {
  command = apply

  assert {
    condition = alltrue([
      for s in aws_subnet.private_app : s.map_public_ip_on_launch == false
    ])
    error_message = "Private application subnets must not auto-assign public IPs."
  }

  assert {
    condition = alltrue([
      for s in aws_subnet.private_database : s.map_public_ip_on_launch == false
    ])
    error_message = "Private database subnets must not auto-assign public IPs."
  }
}

# 12: S3 Gateway VPC Endpoint exists, of the correct type.
run "s3_gateway_endpoint" {
  command = apply

  assert {
    condition     = aws_vpc_endpoint.s3.vpc_endpoint_type == "Gateway"
    error_message = "The S3 VPC endpoint must be of type Gateway (no hourly/processing cost), not Interface."
  }

  assert {
    condition     = aws_vpc_endpoint.s3.service_name == "com.amazonaws.us-east-1.s3"
    error_message = "The S3 VPC endpoint's service name must be built from the configured aws_region."
  }
}

# 13-14: VPC Flow Logs enabled by default, REJECT traffic, 30-day retention.
run "flow_logs_enabled_by_default_with_reject_and_30_day_retention" {
  command = apply

  assert {
    condition     = length(aws_flow_log.main) == 1
    error_message = "VPC Flow Logs must be created when enable_vpc_flow_logs defaults to true."
  }

  assert {
    condition     = aws_flow_log.main[0].traffic_type == "REJECT"
    error_message = "Default Flow Log traffic type must be REJECT."
  }

  assert {
    condition     = aws_cloudwatch_log_group.flow_logs[0].retention_in_days == 30
    error_message = "Default Flow Log retention must be 30 days."
  }
}

# Flow Logs can be deliberately disabled via variable, and the output goes null.
run "flow_logs_disabled_via_variable" {
  command = apply

  variables {
    enable_vpc_flow_logs = false
  }

  assert {
    condition     = length(aws_flow_log.main) == 0
    error_message = "No Flow Log resource must be created when enable_vpc_flow_logs is false."
  }

  assert {
    condition     = length(aws_cloudwatch_log_group.flow_logs) == 0
    error_message = "No Flow Log CloudWatch log group must be created when enable_vpc_flow_logs is false."
  }

  assert {
    condition     = output.flow_log_group_name == null
    error_message = "flow_log_group_name output must be null when Flow Logs are disabled."
  }
}

# 15: Common tags are applied to resources.
run "common_tags_applied_to_vpc" {
  command = apply

  assert {
    condition     = aws_vpc.main.tags["Project"] == "sipsa" && aws_vpc.main.tags["Environment"] == "production" && aws_vpc.main.tags["Owner"] == "test-owner" && aws_vpc.main.tags["ManagedBy"] == "terraform" && aws_vpc.main.tags["Repository"] == "dalejandrov/sipsa" && aws_vpc.main.tags["CostCenter"] == "test-cost-center"
    error_message = "The VPC must carry every common tag from the common_tags variable."
  }

  assert {
    condition     = aws_vpc.main.tags["Name"] == "sipsa-production-vpc"
    error_message = "The VPC must carry a resource-specific Name tag in addition to the common tags."
  }
}

# Invalid input is rejected: wrong subnet count. Uses command = plan, not
# apply — variable validation always fails during planning, and asserting
# an expected failure against `apply` produces a spurious test failure of
# its own (apply can never proceed past a failed plan).
run "rejects_wrong_public_subnet_count" {
  command = plan

  variables {
    public_subnet_cidrs = ["10.40.0.0/24"]
  }

  expect_failures = [
    var.public_subnet_cidrs,
  ]
}

# Invalid input is rejected: malformed CIDR.
run "rejects_invalid_vpc_cidr" {
  command = plan

  variables {
    vpc_cidr = "not-a-cidr"
  }

  expect_failures = [
    var.vpc_cidr,
  ]
}
