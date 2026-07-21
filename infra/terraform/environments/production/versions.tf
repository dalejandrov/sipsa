terraform {
  required_version = "~> 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # Partial backend configuration: the real bucket/table names come from the
  # bootstrap stack's outputs and are supplied at `terraform init` time via
  # `-backend-config=backend.hcl` (see backend.hcl.example), never hardcoded
  # here and never committed with real values.
  backend "s3" {}
}
