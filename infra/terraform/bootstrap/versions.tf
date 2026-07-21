terraform {
  required_version = "~> 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # This stack is the one deliberate exception to "always use a remote backend"
  # (see README.md — Chicken-and-Egg Problem). It provisions the S3 bucket and
  # DynamoDB lock table that every other stack's remote backend depends on, so
  # it cannot depend on that same backend existing yet. State for this stack
  # stays local, applied manually and rarely (only when the backend itself
  # needs to change).
}
