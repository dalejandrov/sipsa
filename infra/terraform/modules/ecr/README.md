# ecr module

ECR repository foundation for SIPSA (TECH-140). No image is published by this module or
this story — `terraform apply` is never run, and no `docker push`/ECR login happens.

## Immutable tags

`image_tag_mutability = "IMMUTABLE"` — once a tag is pushed, it can never be
overwritten. This is why the ECS task definition (`modules/ecs-task`) requires an
explicit, non-`"latest"` image tag: a mutable-by-convention tag would defeat the point of
this setting, since "latest" pointing at different images over time is exactly the
ambiguity immutability exists to prevent.

## Scanning and encryption

`scan_on_push = true` — every pushed image is scanned for known vulnerabilities
automatically. Encryption defaults to `AES256` (AWS-owned key); a customer-managed KMS
key was evaluated but is **not enabled without a real requirement** — no compliance
driver for a dedicated KMS key has been identified for this repository. Switch
`encryption_type = "KMS"` (with a real `kms_key_id`) if that changes.

## Lifecycle policy

Two rules, deliberately not one:

1. **Untagged images expire after `expire_untagged_after_days` (default 7).** An
   untagged image is usually the leftover of a retag or a failed push — 7 days is long
   enough that an in-flight operation touching a temporarily-untagged manifest isn't
   deleted out from under it, short enough that untagged storage doesn't accumulate
   indefinitely.
2. **Tagged images are capped at `keep_last_tagged_images` (default 20), not a time
   window.** A time-based expiry could delete an image that is still the one actually
   deployed in production if a release goes unusually long between deploys — a count-based
   cap never deletes the currently-referenced image as long as fewer than 20 images have
   been pushed since it was deployed, which is the safer failure mode. 20 is a starting,
   conservative proposal, not a measured number — revisit once a real release cadence
   exists.

No cross-region replication is configured — this repository has one deployment region
(`us-east-1`, ADR-010); replication would be unused cost.

## Testing

`tests/ecr.tftest.hcl` uses Terraform's native `terraform test` with a mocked AWS
provider (`mock_provider "aws" {}`) — no real AWS account or credential is contacted, and
no image is ever pushed. Run with `terraform test` from this module's directory (after
`terraform init -backend=false`).
