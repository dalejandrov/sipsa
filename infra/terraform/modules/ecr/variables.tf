variable "project_name" {
  description = "Short project identifier, forwarded into resource Name tags."
  type        = string
}

variable "environment" {
  description = "Deployment environment, forwarded into resource Name tags."
  type        = string
}

variable "common_tags" {
  description = "Tag set applied to every resource in this module, on top of a resource-specific Name tag."
  type        = map(string)
}

variable "repository_name" {
  description = "ECR repository name. Defaults to \"<project_name>/<environment>-app\"."
  type        = string
  default     = null
}

variable "encryption_type" {
  description = "ECR encryption type. Default AES256 (AWS-owned key) — evaluated against a customer-managed KMS key, but not enabled without a real requirement (no compliance driver identified for this repository yet)."
  type        = string
  default     = "AES256"

  validation {
    condition     = contains(["AES256", "KMS"], var.encryption_type)
    error_message = "encryption_type must be AES256 or KMS."
  }
}

variable "kms_key_id" {
  description = "KMS key ID/ARN to use when encryption_type is \"KMS\". Ignored otherwise. No default — must not be invented."
  type        = string
  default     = null
}

variable "keep_last_tagged_images" {
  description = "Number of tagged images to retain — older tagged images beyond this count expire. Default 20: conservative enough to keep a meaningful rollback window without unbounded storage growth."
  type        = number
  default     = 20

  validation {
    condition     = var.keep_last_tagged_images >= 1
    error_message = "keep_last_tagged_images must be at least 1."
  }
}

variable "expire_untagged_after_days" {
  description = <<-EOT
    Days after which an untagged image expires. Default 7: long enough that
    an image which briefly loses its tag during a retag/promotion operation
    is not deleted out from under an in-flight operation, short enough that
    untagged image storage does not accumulate indefinitely.
  EOT
  type        = number
  default     = 7

  validation {
    condition     = var.expire_untagged_after_days >= 1
    error_message = "expire_untagged_after_days must be at least 1."
  }
}
