provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    Owner       = var.owner
    ManagedBy   = var.managed_by
    Repository  = var.repository
    CostCenter  = var.cost_center
  }
}

# Remote state bucket. Every other Terraform stack in this repository (starting
# with environments/production) configures its backend to point here, using
# the S3-native lockfile mechanism (`use_lockfile = true`) rather than a
# DynamoDB lock table — no separate locking resource is provisioned by this
# stack. See README.md for why.
#
# No access-logging bucket: it would require its own bucket and lifecycle
# policy purely to log access to a bucket only the manual bootstrap process
# (a single owner, per ADR-010) ever touches. Documented as a future
# hardening step if that ownership model changes, not implemented
# preemptively.
# tfsec:ignore:aws-s3-enable-bucket-logging
resource "aws_s3_bucket" "terraform_state" {
  bucket = var.state_bucket_name

  # Deliberately no lifecycle "prevent_destroy" here yet: this stack is applied
  # manually and rarely, and adding prevent_destroy before the bucket has real
  # state in it would just make legitimate re-bootstrapping harder. Revisit once
  # this bucket holds real production state.
}

resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

# AES256 (AWS-owned key), not a customer-managed KMS key: this bucket only
# ever holds Terraform state, touched solely by the manual bootstrap process
# (see README.md) — a dedicated KMS key/policy for it is complexity this
# stage doesn't need. Revisit if that changes.
# tfsec:ignore:aws-s3-encryption-customer-key
resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Ownership controls: this account is the sole writer to this bucket (the
# manual bootstrap process), so ACLs are disabled outright rather than left
# in an ambiguous default state.
resource "aws_s3_bucket_ownership_controls" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# Old state versions are cleared out after 90 days rather than kept forever —
# versioning above protects against accidental overwrite/delete in the near
# term (the scenario it exists for); indefinite retention of every historical
# state version has no corresponding benefit once that window has passed.
resource "aws_s3_bucket_lifecycle_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    id     = "expire-noncurrent-state-versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}
