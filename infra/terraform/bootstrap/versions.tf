terraform {
  # S3-native state locking (used by environments/production's backend, not
  # this stack) requires a modern Terraform client. This constraint is
  # deliberately not widened to support older Terraform clients that only
  # know the DynamoDB-lock pattern — see README.md and
  # environments/production/versions.tf.
  required_version = ">= 1.14.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 6.0.0, < 7.0.0"
    }
  }

  # This stack is the one deliberate exception to "always use a remote backend"
  # (see README.md — Chicken-and-Egg Problem). It provisions the S3 bucket
  # every other stack's remote backend depends on, so it cannot depend on
  # that same backend existing yet. State for this stack stays local, applied
  # manually and rarely (only when the backend itself needs to change).
}
