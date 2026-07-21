variable "project_name" {
  description = "Short project identifier, forwarded into resource Name tags."
  type        = string
}

variable "aws_region" {
  description = "AWS region this module deploys into — used to construct the S3 Gateway VPC Endpoint's service name. Passed explicitly rather than read back from a data source, since the caller already knows it (ADR-010: us-east-1)."
  type        = string
}

variable "environment" {
  description = "Deployment environment, forwarded into resource Name tags."
  type        = string
}

variable "common_tags" {
  description = "Tag set applied to every resource in this module, on top of a resource-specific Name tag. Supplied by the root module's local.common_tags (TECH-137) — this module does not define its own tagging convention."
  type        = map(string)
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC (TECH-138: 10.40.0.0/16)."
  type        = string

  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0))
    error_message = "vpc_cidr must be a valid IPv4 CIDR block."
  }
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for the two public subnets, one per selected AZ, in AZ order."
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_cidrs) == 2
    error_message = "Exactly two public subnet CIDRs are required (one per AZ) — TECH-138 does not implement more than two AZs."
  }

  validation {
    condition     = alltrue([for c in var.public_subnet_cidrs : can(cidrhost(c, 0))])
    error_message = "Every public subnet CIDR must be a valid IPv4 CIDR block."
  }
}

variable "private_app_subnet_cidrs" {
  description = "CIDR blocks for the two private application subnets (ECS), one per selected AZ, in AZ order."
  type        = list(string)

  validation {
    condition     = length(var.private_app_subnet_cidrs) == 2
    error_message = "Exactly two private application subnet CIDRs are required (one per AZ)."
  }

  validation {
    condition     = alltrue([for c in var.private_app_subnet_cidrs : can(cidrhost(c, 0))])
    error_message = "Every private application subnet CIDR must be a valid IPv4 CIDR block."
  }
}

variable "private_database_subnet_cidrs" {
  description = "CIDR blocks for the two private database subnets (future RDS subnet group), one per selected AZ, in AZ order."
  type        = list(string)

  validation {
    condition     = length(var.private_database_subnet_cidrs) == 2
    error_message = "Exactly two private database subnet CIDRs are required (one per AZ)."
  }

  validation {
    condition     = alltrue([for c in var.private_database_subnet_cidrs : can(cidrhost(c, 0))])
    error_message = "Every private database subnet CIDR must be a valid IPv4 CIDR block."
  }
}

variable "enable_vpc_flow_logs" {
  description = "Whether to create VPC Flow Logs (CloudWatch Logs destination, dedicated IAM role). Recommended true for production; a conscious variable, not a hardcoded default, so it can be deliberately disabled if ever needed."
  type        = bool
  default     = true
}

variable "flow_log_traffic_type" {
  description = <<-EOT
    Traffic captured by VPC Flow Logs when enabled. Defaults to REJECT: rejected
    traffic is the most useful signal for diagnosing network/security-group
    misconfiguration, and capturing only REJECT (not ALL) keeps log volume — and
    cost — proportional to actual connectivity problems rather than every
    accepted packet.
  EOT
  type        = string
  default     = "REJECT"

  validation {
    condition     = contains(["ACCEPT", "REJECT", "ALL"], var.flow_log_traffic_type)
    error_message = "flow_log_traffic_type must be one of: ACCEPT, REJECT, ALL."
  }
}

variable "flow_log_retention_days" {
  description = "CloudWatch Logs retention, in days, for VPC Flow Logs when enabled. No default of 0 (infinite) is permitted."
  type        = number
  default     = 30

  validation {
    condition     = var.flow_log_retention_days > 0
    error_message = "flow_log_retention_days must be positive — indefinite retention is not permitted."
  }
}
