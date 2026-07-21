variable "project_name" {
  description = "Short project identifier used in resource names and tags."
  type        = string
  default     = "sipsa"
}

variable "environment" {
  description = "Deployment environment. ADR-010: production only for now — kept as a variable, not hardcoded, so a future environment does not require restructuring this module."
  type        = string
  default     = "production"
}

variable "aws_region" {
  description = "AWS region for every resource in this stack (ADR-010: us-east-1)."
  type        = string
  default     = "us-east-1"
}

variable "owner" {
  description = "Person or team accountable for this infrastructure (ADR-010: repository owner, initially)."
  type        = string
}

variable "cost_center" {
  description = "Cost center for billing attribution. No default — must not be invented; supply the real value at apply time."
  type        = string
}

variable "repository" {
  description = "Source repository for this infrastructure, for traceability in tags."
  type        = string
  default     = "dalejandrov/sipsa"
}

variable "managed_by" {
  description = "Tooling that manages these resources, for the ManagedBy tag."
  type        = string
  default     = "terraform"
}

# --- Network (TECH-138) ---------------------------------------------------
# Defaults match ADR-010's approved topology. Validation happens in the
# network module itself (the actual enforcement point); these are pass-
# through variables so the topology can be overridden per apply without
# editing module code.

variable "vpc_cidr" {
  description = "CIDR block for the production VPC (ADR-010/TECH-138: 10.40.0.0/16)."
  type        = string
  default     = "10.40.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for the two public subnets, one per AZ."
  type        = list(string)
  default     = ["10.40.0.0/24", "10.40.1.0/24"]
}

variable "private_app_subnet_cidrs" {
  description = "CIDR blocks for the two private application (ECS) subnets, one per AZ."
  type        = list(string)
  default     = ["10.40.10.0/24", "10.40.11.0/24"]
}

variable "private_database_subnet_cidrs" {
  description = "CIDR blocks for the two private database subnets, one per AZ."
  type        = list(string)
  default     = ["10.40.20.0/24", "10.40.21.0/24"]
}

variable "enable_vpc_flow_logs" {
  description = "Whether to create VPC Flow Logs. ADR-010/TECH-138: true for production."
  type        = bool
  default     = true
}

variable "flow_log_traffic_type" {
  description = "VPC Flow Log traffic type when enabled. ADR-010/TECH-138: REJECT (reduces volume, keeps the diagnostically useful signal)."
  type        = string
  default     = "REJECT"
}

variable "flow_log_retention_days" {
  description = "CloudWatch Logs retention, in days, for VPC Flow Logs when enabled. ADR-010/TECH-138: 30 days — never infinite."
  type        = number
  default     = 30
}
