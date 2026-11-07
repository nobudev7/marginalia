# ==============================================================================
# Marginalia — S3 Off-Site Backup Storage Input Variables
# ==============================================================================

variable "aws_region" {
  description = "AWS region where the S3 backup bucket is provisioned."
  type        = string
  default     = "us-east-1"
}

variable "name_prefix" {
  description = "Prefix used for resource Name tags and optional IAM uploader username."
  type        = string
  default     = "marginalia"
}

variable "s3_bucket_name" {
  description = "Explicit name for the S3 backup bucket (must be globally unique). If null, s3_bucket_prefix is used."
  type        = string
  default     = null
}

variable "s3_bucket_prefix" {
  description = "Prefix for the S3 backup bucket name when s3_bucket_name is null."
  type        = string
  default     = "marginalia-backup-"
}

variable "s3_backup_retention_days" {
  description = "Number of days after which backup archives in S3 are automatically deleted (0 disables lifecycle expiration)."
  type        = number
  default     = 90

  validation {
    condition     = var.s3_backup_retention_days >= 0
    error_message = "s3_backup_retention_days must be 0 or a positive integer."
  }
}

variable "s3_force_destroy" {
  description = "Allow destroying the S3 bucket even if it contains objects. Keep false for production safety."
  type        = bool
  default     = false
}

variable "create_s3_backup_iam_user" {
  description = "Whether to create a dedicated IAM user and policy with restricted access to the S3 backup bucket."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Additional tags applied to AWS resources."
  type        = map(string)
  default     = {}
}
