# Structural tests for the database module (TECH-139). No real AWS account is
# contacted — the AWS provider is fully mocked (mock_provider "aws" {}
# below); `command = apply` never creates anything real, it only lets
# Terraform's mock provider resolve computed attributes (IDs, ARNs) that stay
# unknown under `command = plan`. The `random` provider is real (not mocked)
# but makes no network call of any kind — random_id is pure local
# computation.
#
# Two criteria from this module's acceptance checklist are NOT expressed as
# runtime assertions here, because they are structural facts about the
# module's own variables/outputs.tf, not something a mock apply can
# meaningfully differ on:
#   - "no password in variables": variables.tf defines no `password` or
#     `master_password` variable anywhere — confirmed by inspection
#     (grep -n "variable \"password\"" variables.tf returns nothing), not by
#     a runtime assertion.
#   - "outputs don't reveal secrets": outputs.tf declares db_endpoint
#     `sensitive = true`; master_secret_arn is documented as ARN-only (never
#     the secret value) directly in its description. Both are properties of
#     the .tf source, verified by reading it, not by an apply-time check.
#
# Run from infra/terraform/modules/database/ with: terraform test

mock_provider "aws" {}

# The mock provider's zero-value default for a computed nested block
# (master_user_secret) is an empty list, not a single populated element —
# real RDS always returns exactly one when manage_master_user_password is
# true. Overridden here purely to make that block resolvable in assertions;
# no real AWS account or secret is involved.
override_resource {
  target = aws_db_instance.main
  values = {
    master_user_secret = [
      {
        secret_arn    = "arn:aws:secretsmanager:us-east-1:123456789012:secret:rds!db-example-abc123"
        secret_status = "active"
        kms_key_id    = "arn:aws:kms:us-east-1:123456789012:key:example-key-id"
      }
    ]
  }
}

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
  vpc_id                      = "vpc-0123456789abcdef0"
  private_database_subnet_ids = ["subnet-db-a", "subnet-db-b"]
}

# 1-2: DB subnet group uses exactly the two DB subnets it was given — never
# any other subnet.
run "db_subnet_group_uses_only_database_subnets" {
  command = apply

  assert {
    condition     = length(aws_db_subnet_group.main.subnet_ids) == 2
    error_message = "The DB subnet group must use exactly two subnets."
  }

  assert {
    condition     = toset(aws_db_subnet_group.main.subnet_ids) == toset(["subnet-db-a", "subnet-db-b"])
    error_message = "The DB subnet group must use exactly the private database subnet IDs it was given — never any other subnet."
  }
}

# 3: publicly_accessible is hardcoded false, not a variable.
run "instance_is_not_publicly_accessible" {
  command = apply

  assert {
    condition     = aws_db_instance.main.publicly_accessible == false
    error_message = "The RDS instance must never be publicly accessible."
  }
}

# 4 / 14: Single-AZ by default.
run "single_az_by_default" {
  command = apply

  assert {
    condition     = aws_db_instance.main.multi_az == false
    error_message = "multi_az must default to false (ADR-010: Single-AZ initially, a documented cost trade-off)."
  }
}

# 5: Storage encryption is hardcoded true, not a variable.
run "storage_is_encrypted" {
  command = apply

  assert {
    condition     = aws_db_instance.main.storage_encrypted == true
    error_message = "storage_encrypted must always be true — this is not configurable."
  }
}

# 6: Default backup retention is 7 days.
run "default_backup_retention_is_seven_days" {
  command = apply

  assert {
    condition     = aws_db_instance.main.backup_retention_period == 7
    error_message = "backup_retention_period must default to 7 days (ADR-010)."
  }
}

# 7: Deletion protection is enabled by default.
run "deletion_protection_enabled_by_default" {
  command = apply

  assert {
    condition     = aws_db_instance.main.deletion_protection == true
    error_message = "deletion_protection must default to true (ADR-010)."
  }
}

# 8: A final snapshot is required by default, with a reproducible-but-unique
# identifier (never a static name).
run "final_snapshot_required_by_default" {
  command = apply

  assert {
    condition     = aws_db_instance.main.skip_final_snapshot == false
    error_message = "skip_final_snapshot must default to false — a final snapshot must be taken on deletion."
  }

  assert {
    condition     = aws_db_instance.main.final_snapshot_identifier != null && aws_db_instance.main.final_snapshot_identifier != ""
    error_message = "final_snapshot_identifier must be set whenever skip_final_snapshot is false."
  }

  assert {
    condition     = strcontains(aws_db_instance.main.final_snapshot_identifier, random_id.final_snapshot_suffix.hex)
    error_message = "final_snapshot_identifier must incorporate the random, unique suffix — never a static name that a second destroy/recreate cycle could collide with."
  }
}

# 9: RDS manages the master password directly in Secrets Manager.
run "rds_manages_master_password" {
  command = apply

  assert {
    condition     = aws_db_instance.main.manage_master_user_password == true
    error_message = "manage_master_user_password must be true — RDS, not Terraform, must own the master credential."
  }

  assert {
    condition     = can(aws_db_instance.main.master_user_secret[0].secret_arn)
    error_message = "The RDS-managed secret's ARN must be resolvable from the instance's master_user_secret attribute."
  }
}

# 11: No ingress rule exists when no consumer security group is configured
# (the default) — never a global/CIDR-based rule.
run "no_ingress_rule_without_a_configured_consumer" {
  command = apply

  assert {
    condition     = length(aws_security_group_rule.rds_ingress) == 0
    error_message = "No ingress rule must exist when allowed_security_group_ids is empty (the default) — this story creates the security group only, no rule."
  }
}

# A consumer security group ID, when configured, produces exactly one
# security-group-scoped ingress rule — never a CIDR block.
run "ingress_rule_created_per_allowed_security_group" {
  command = apply

  variables {
    allowed_security_group_ids = ["sg-ecs-example"]
  }

  assert {
    condition     = length(aws_security_group_rule.rds_ingress) == 1
    error_message = "Exactly one ingress rule must be created per entry in allowed_security_group_ids."
  }

  assert {
    condition     = aws_security_group_rule.rds_ingress[0].source_security_group_id == "sg-ecs-example"
    error_message = "The ingress rule must be scoped to the given security group ID, not a CIDR block."
  }

  assert {
    condition     = aws_security_group_rule.rds_ingress[0].cidr_blocks == null || length(aws_security_group_rule.rds_ingress[0].cidr_blocks) == 0
    error_message = "The ingress rule must never carry a CIDR block."
  }
}

# 12: Default port is 5432.
run "default_port_is_5432" {
  command = apply

  assert {
    condition     = aws_db_instance.main.port == 5432
    error_message = "The default PostgreSQL port must be 5432."
  }
}

# 13: Common tags are applied to the instance.
run "common_tags_applied_to_instance" {
  command = apply

  assert {
    condition     = aws_db_instance.main.tags["Project"] == "sipsa" && aws_db_instance.main.tags["Environment"] == "production" && aws_db_instance.main.tags["Owner"] == "test-owner" && aws_db_instance.main.tags["ManagedBy"] == "terraform" && aws_db_instance.main.tags["Repository"] == "dalejandrov/sipsa" && aws_db_instance.main.tags["CostCenter"] == "test-cost-center"
    error_message = "The RDS instance must carry every common tag."
  }

  assert {
    condition     = aws_db_instance.main.tags["Name"] == "sipsa-production-postgres"
    error_message = "The RDS instance must carry a resource-specific Name tag in addition to the common tags."
  }
}

# 15: Instance class is a variable, not hardcoded.
run "instance_class_is_parameterizable" {
  command = apply

  variables {
    instance_class = "db.t3.small"
  }

  assert {
    condition     = aws_db_instance.main.instance_class == "db.t3.small"
    error_message = "instance_class must be overridable via variable."
  }
}

# 16: Storage is parameterizable, and the default satisfies the gp3 minimum.
run "storage_is_parameterizable_with_a_valid_default" {
  command = apply

  assert {
    condition     = aws_db_instance.main.allocated_storage == 20
    error_message = "Default allocated_storage must be 20 GiB (the gp3 minimum)."
  }

  assert {
    condition     = aws_db_instance.main.storage_type == "gp3"
    error_message = "Default storage_type must be gp3."
  }
}

run "storage_overrides_take_effect" {
  command = apply

  variables {
    allocated_storage     = 50
    max_allocated_storage = 200
  }

  assert {
    condition     = aws_db_instance.main.allocated_storage == 50 && aws_db_instance.main.max_allocated_storage == 200
    error_message = "Storage overrides must take effect."
  }
}

# 17: Performance Insights is not enabled by default.
run "performance_insights_disabled_by_default" {
  command = apply

  assert {
    condition     = aws_db_instance.main.performance_insights_enabled == false
    error_message = "performance_insights_enabled must default to false — an added cost with no established need yet."
  }

  assert {
    condition     = aws_db_instance.main.monitoring_interval == 0
    error_message = "monitoring_interval (Enhanced Monitoring) must default to 0 (disabled)."
  }
}

# CloudWatch log groups: created with explicit, non-infinite retention.
run "log_groups_have_explicit_retention" {
  command = apply

  assert {
    condition     = length(aws_cloudwatch_log_group.postgresql) == 1 && aws_cloudwatch_log_group.postgresql[0].retention_in_days == 30
    error_message = "The postgresql log group must exist with 30-day retention by default."
  }

  assert {
    condition     = length(aws_cloudwatch_log_group.upgrade) == 1 && aws_cloudwatch_log_group.upgrade[0].retention_in_days == 30
    error_message = "The upgrade log group must exist with 30-day retention by default."
  }
}

# Invalid input is rejected: too few DB subnets.
run "rejects_fewer_than_two_database_subnets" {
  command = plan

  variables {
    private_database_subnet_ids = ["subnet-db-a"]
  }

  expect_failures = [
    var.private_database_subnet_ids,
  ]
}

# Invalid input is rejected: storage below the gp3 minimum.
run "rejects_allocated_storage_below_minimum" {
  command = plan

  variables {
    allocated_storage = 5
  }

  expect_failures = [
    var.allocated_storage,
  ]
}

# Invalid input is rejected: Provisioned IOPS storage types are not allowed.
run "rejects_provisioned_iops_storage_type" {
  command = plan

  variables {
    storage_type = "io2"
  }

  expect_failures = [
    var.storage_type,
  ]
}
