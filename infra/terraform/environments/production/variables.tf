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
