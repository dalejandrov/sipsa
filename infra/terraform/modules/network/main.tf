# Two AZs, selected deterministically (never hardcoded — see README.md).
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)

  name_prefix = "${var.project_name}-${var.environment}"
}

# ---------------------------------------------------------------------------
# VPC
# ---------------------------------------------------------------------------

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-vpc"
  })
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-igw"
  })
}

# ---------------------------------------------------------------------------
# Subnets — three tiers, two AZs each
# ---------------------------------------------------------------------------

# map_public_ip_on_launch = true is intentional for this tier: this is the
# VPC's public subnet, where the NAT Gateway's EIP-backed ENI lives (and
# where a future public-facing ALB, if one is ever needed, would too) — an
# auto-assigned public IP is the expected behavior for the public tier
# specifically, not an oversight. No workload runs directly in these
# subnets; ECS (TECH-132) is placed in the private application tier, which
# has map_public_ip_on_launch = false below.
# trivy:ignore:AVD-AWS-0164
resource "aws_subnet" "public" {
  count = 2

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-public-${local.azs[count.index]}"
    Tier = "public"
  })
}

resource "aws_subnet" "private_app" {
  count = 2

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.private_app_subnet_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = false

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-private-app-${local.azs[count.index]}"
    Tier = "private-application"
  })
}

resource "aws_subnet" "private_database" {
  count = 2

  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.private_database_subnet_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = false

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-private-db-${local.azs[count.index]}"
    Tier = "private-database"
  })
}

# ---------------------------------------------------------------------------
# NAT — single gateway, first public subnet (TECH-138 / ADR-010 accepted risk)
# ---------------------------------------------------------------------------

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-nat-eip"
  })

  depends_on = [aws_internet_gateway.main]
}

# Single NAT Gateway, in the first selected AZ's public subnet. Both private
# application subnets route through it — see README.md for the documented
# cost/availability trade-off and the future per-AZ NAT migration path.
resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-nat"
  })

  depends_on = [aws_internet_gateway.main]
}

# ---------------------------------------------------------------------------
# Route tables — public (shared), private-app (per AZ), database (per AZ)
# ---------------------------------------------------------------------------

# One shared public route table: both public subnets route identically to the
# IGW, with no anticipated per-AZ divergence for public egress.
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-public-rt"
  })
}

resource "aws_route_table_association" "public" {
  count = 2

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Per-AZ private-app route tables (not one shared table): both currently
# route to the same single NAT Gateway, but keeping them separate means the
# future NAT-per-AZ migration (README.md) re-points one AZ's table to its own
# NAT without touching the other AZ's table.
resource "aws_route_table" "private_app" {
  count = 2

  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-private-app-rt-${local.azs[count.index]}"
  })
}

resource "aws_route_table_association" "private_app" {
  count = 2

  subnet_id      = aws_subnet.private_app[count.index].id
  route_table_id = aws_route_table.private_app[count.index].id
}

# Per-AZ database route tables: no route to the NAT Gateway or the Internet
# Gateway is declared — only the implicit local VPC route exists. Reserved
# for a future RDS DB subnet group (TECH-132); kept per-AZ for symmetry with
# the other two tiers and to allow future per-AZ customization (e.g. a
# region-specific VPC endpoint) without restructuring.
resource "aws_route_table" "database" {
  count = 2

  vpc_id = aws_vpc.main.id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-database-rt-${local.azs[count.index]}"
  })
}

resource "aws_route_table_association" "database" {
  count = 2

  subnet_id      = aws_subnet.private_database[count.index].id
  route_table_id = aws_route_table.database[count.index].id
}

# ---------------------------------------------------------------------------
# S3 Gateway VPC Endpoint — no hourly/processing cost, keeps S3 traffic off
# the NAT Gateway (useful today for any S3-backed artifact access, and for
# ECR image layers once TECH-132 introduces ECS, since ECR stores image
# layers in S3).
# ---------------------------------------------------------------------------

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"

  route_table_ids = aws_route_table.private_app[*].id

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-s3-gateway-endpoint"
  })
}

# ---------------------------------------------------------------------------
# VPC Flow Logs — configurable, default enabled, REJECT only, explicit
# retention (TECH-138 §15).
# ---------------------------------------------------------------------------

# AWS-owned key (CloudWatch Logs' own default encryption), not a
# customer-managed KMS key: this log group holds REJECT-only VPC Flow Logs
# (network metadata, not application data), and a dedicated KMS key/policy
# for it is complexity this stage doesn't need — consistent with the same
# call already made for the Terraform state bucket (bootstrap/main.tf).
# Revisit if a compliance requirement makes this necessary.
# trivy:ignore:AVD-AWS-0017
resource "aws_cloudwatch_log_group" "flow_logs" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  name              = "/vpc-flow-logs/${local.name_prefix}"
  retention_in_days = var.flow_log_retention_days

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-flow-logs"
  })
}

resource "aws_iam_role" "flow_logs" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  name = "${local.name_prefix}-vpc-flow-logs"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "vpc-flow-logs.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = var.common_tags
}

# Minimal policy: only the three actions VPC Flow Logs needs, scoped to this
# module's own log group — not a wildcard resource, not broader CloudWatch
# Logs access.
resource "aws_iam_role_policy" "flow_logs" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  name = "${local.name_prefix}-vpc-flow-logs"
  role = aws_iam_role.flow_logs[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams",
      ]
      Resource = "${aws_cloudwatch_log_group.flow_logs[0].arn}:*"
    }]
  })
}

resource "aws_flow_log" "main" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  vpc_id               = aws_vpc.main.id
  traffic_type         = var.flow_log_traffic_type
  log_destination_type = "cloud-watch-logs"
  log_destination      = aws_cloudwatch_log_group.flow_logs[0].arn
  iam_role_arn         = aws_iam_role.flow_logs[0].arn

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-flow-log"
  })
}
