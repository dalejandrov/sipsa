variable "aws_region" {
  description = "AWS region for the state bucket and lock table (ADR-010: us-east-1)."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Short project identifier used in resource names and tags."
  type        = string
  default     = "sipsa"
}

variable "environment" {
  description = "Environment this backend serves. ADR-010: production only for now."
  type        = string
  default     = "production"
}

variable "owner" {
  description = "Person or team accountable for this infrastructure (ADR-010: repository owner, initially)."
  type        = string
}

variable "cost_center" {
  description = "Cost center for billing attribution. No value is invented here; supply the real one at apply time."
  type        = string
}

variable "repository" {
  description = "Source repository for this infrastructure, for traceability in tags."
  type        = string
  default     = "dalejandrov/sipsa"
}

variable "managed_by" {
  description = "Tooling that manages this resource, for the ManagedBy tag."
  type        = string
  default     = "terraform"
}

variable "state_bucket_name" {
  description = <<-EOT
    Name of the S3 bucket that stores Terraform remote state for every other stack.
    S3 bucket names are globally unique across all of AWS, not just this account —
    this has no default on purpose. Choose a name before running this stack, e.g.
    "sipsa-terraform-state-<random-suffix-or-account-id>".
  EOT
  type        = string
}
