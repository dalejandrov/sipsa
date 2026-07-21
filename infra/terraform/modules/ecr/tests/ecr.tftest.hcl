# Structural tests for the ecr module (TECH-140). No real AWS account is
# contacted — the AWS provider is fully mocked (mock_provider "aws" {}). No
# image is pushed at any point.

mock_provider "aws" {}

variables {
  project_name = "sipsa"
  environment  = "production"
  common_tags = {
    Project     = "sipsa"
    Environment = "production"
    Owner       = "test-owner"
    ManagedBy   = "terraform"
    Repository  = "dalejandrov/sipsa"
    CostCenter  = "test-cost-center"
  }
}

# 1: Image tags are immutable.
run "tags_are_immutable" {
  command = apply

  assert {
    condition     = aws_ecr_repository.app.image_tag_mutability == "IMMUTABLE"
    error_message = "image_tag_mutability must be IMMUTABLE."
  }
}

# 2: Scan on push is enabled.
run "scan_on_push_enabled" {
  command = apply

  assert {
    condition     = aws_ecr_repository.app.image_scanning_configuration[0].scan_on_push == true
    error_message = "scan_on_push must be true."
  }
}

# 3: Encryption defaults to AES256.
run "encryption_defaults_to_aes256" {
  command = apply

  assert {
    condition     = aws_ecr_repository.app.encryption_configuration[0].encryption_type == "AES256"
    error_message = "Default encryption_type must be AES256."
  }
}

# KMS encryption can be selected explicitly.
run "kms_encryption_can_be_selected" {
  command = apply

  variables {
    encryption_type = "KMS"
    kms_key_id      = "arn:aws:kms:us-east-1:123456789012:key:example-key-id"
  }

  assert {
    condition     = aws_ecr_repository.app.encryption_configuration[0].encryption_type == "KMS"
    error_message = "encryption_type must be overridable to KMS."
  }
}

# 4: Lifecycle policy caps tagged images at the configured count.
run "lifecycle_policy_caps_tagged_images" {
  command = apply

  assert {
    condition     = strcontains(aws_ecr_lifecycle_policy.app.policy, "\"countNumber\":20")
    error_message = "The lifecycle policy must cap tagged images at the default keep_last_tagged_images (20)."
  }

  assert {
    condition     = strcontains(aws_ecr_lifecycle_policy.app.policy, "\"tagStatus\":\"tagged\"")
    error_message = "The lifecycle policy must include a rule targeting tagged images."
  }
}

# 5: Lifecycle policy expires untagged images after the configured period.
run "lifecycle_policy_expires_untagged_images" {
  command = apply

  assert {
    condition     = strcontains(aws_ecr_lifecycle_policy.app.policy, "\"tagStatus\":\"untagged\"")
    error_message = "The lifecycle policy must include a rule targeting untagged images."
  }

  assert {
    condition     = strcontains(aws_ecr_lifecycle_policy.app.policy, "\"countNumber\":7")
    error_message = "The lifecycle policy must expire untagged images after the default expire_untagged_after_days (7)."
  }
}

# Lifecycle values are parameterizable.
run "lifecycle_values_are_parameterizable" {
  command = apply

  variables {
    keep_last_tagged_images    = 5
    expire_untagged_after_days = 3
  }

  assert {
    condition     = strcontains(aws_ecr_lifecycle_policy.app.policy, "\"countNumber\":5") && strcontains(aws_ecr_lifecycle_policy.app.policy, "\"countNumber\":3")
    error_message = "Lifecycle policy counts must reflect variable overrides."
  }
}

# 6: Common tags are applied.
run "common_tags_applied" {
  command = apply

  assert {
    condition     = aws_ecr_repository.app.tags["Project"] == "sipsa" && aws_ecr_repository.app.tags["Environment"] == "production" && aws_ecr_repository.app.tags["Owner"] == "test-owner" && aws_ecr_repository.app.tags["ManagedBy"] == "terraform" && aws_ecr_repository.app.tags["Repository"] == "dalejandrov/sipsa" && aws_ecr_repository.app.tags["CostCenter"] == "test-cost-center"
    error_message = "The ECR repository must carry every common tag."
  }

  assert {
    condition     = aws_ecr_repository.app.tags["Name"] == "sipsa-production-app"
    error_message = "The ECR repository must carry a resource-specific Name tag."
  }
}

# Invalid input is rejected: unsupported encryption type.
run "rejects_invalid_encryption_type" {
  command = plan

  variables {
    encryption_type = "unsupported"
  }

  expect_failures = [
    var.encryption_type,
  ]
}
