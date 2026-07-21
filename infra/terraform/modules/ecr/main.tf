locals {
  name_prefix     = "${var.project_name}-${var.environment}"
  repository_name = coalesce(var.repository_name, "${var.project_name}/${var.environment}-app")
}

# IMMUTABLE tags: a tag, once pushed, can never be overwritten — the only
# way "v1.2.3" (or whatever tag scheme is eventually adopted) can ever mean
# two different images is by mistake, not by design. This is why the task
# definition's image_tag variable (modules/ecs-task) must reject "latest" —
# a mutable-by-convention tag would defeat the point of this setting.
#
# encryption_type defaults to AES256 (AWS-owned key), not a customer-managed
# KMS key: evaluated, but not enabled without a real requirement — no
# compliance driver for a dedicated KMS key has been identified for this
# repository's container images, consistent with the same call already made
# for the Terraform state bucket and other log groups in this codebase.
# KMS remains fully selectable via encryption_type/kms_key_id if that changes.
# trivy:ignore:AVD-AWS-0033
resource "aws_ecr_repository" "app" {
  name                 = local.repository_name
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = var.encryption_type
    kms_key         = var.encryption_type == "KMS" ? var.kms_key_id : null
  }

  tags = merge(var.common_tags, {
    Name = "${local.name_prefix}-app"
  })
}

# Two-rule lifecycle: untagged images (orphaned by a retag, or a failed push
# that left a manifest without a tag) expire after a short grace period;
# tagged images are capped at a generous count rather than a time window —
# a time-based expiry could delete an image that is still the one deployed
# in production if a release goes unusually long between deploys.
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after ${var.expire_untagged_after_days} days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.expire_untagged_after_days
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Keep only the last ${var.keep_last_tagged_images} tagged images"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["*"]
          countType      = "imageCountMoreThan"
          countNumber    = var.keep_last_tagged_images
        }
        action = {
          type = "expire"
        }
      },
    ]
  })
}
