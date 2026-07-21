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
# with environments/production) configures its backend to point here.
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

# State locking. DynamoDB is used here (rather than S3-native locking) for the
# widest compatibility with the pinned Terraform 1.9 line and with any CI
# tooling that expects the traditional DynamoDB-lock pattern.
resource "aws_dynamodb_table" "terraform_locks" {
  name         = var.lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}
