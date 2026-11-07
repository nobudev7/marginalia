# ==============================================================================
# Marginalia — Standalone AWS S3 Off-Site Backup Storage Stack
#
# Provisions:
#   - Private, dedicated S3 bucket for encrypted MySQL disaster-recovery backups
#   - Default AES256 server-side encryption (SSE-S3)
#   - Strict public access block (blocks all public ACLs and bucket policies)
#   - Lifecycle expiration rule for archives older than var.s3_backup_retention_days
#   - Optional least-privilege IAM user + access key for host backup uploads
#
# Kept in an independent stack so backups survive compute migrations and
# `terraform destroy` runs on ../aws-lightsail or ../oci untouched.
# ==============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0, < 7.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = merge(
      {
        Project   = "marginalia"
        ManagedBy = "terraform"
      },
      var.tags,
    )
  }
}

# ------------------------------------------------------------------------------
# S3 Bucket
# ------------------------------------------------------------------------------
resource "aws_s3_bucket" "backup" {
  bucket        = var.s3_bucket_name
  bucket_prefix = var.s3_bucket_name == null ? var.s3_bucket_prefix : null
  force_destroy = var.s3_force_destroy

  tags = {
    Name    = "${var.name_prefix}-backup-bucket"
    Purpose = "Database disaster recovery backups"
  }
}

# ------------------------------------------------------------------------------
# Server-Side Encryption (SSE-S3 / AES256)
# ------------------------------------------------------------------------------
resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# ------------------------------------------------------------------------------
# Public Access Block (Strict Private Security)
# ------------------------------------------------------------------------------
resource "aws_s3_bucket_public_access_block" "backup" {
  bucket = aws_s3_bucket.backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ------------------------------------------------------------------------------
# Bucket Lifecycle Policy (Automated Cloud Retention Rotation)
# ------------------------------------------------------------------------------
resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  count  = var.s3_backup_retention_days > 0 ? 1 : 0
  bucket = aws_s3_bucket.backup.id

  rule {
    id     = "expire-old-backups"
    status = "Enabled"

    filter {}

    expiration {
      days = var.s3_backup_retention_days
    }
  }
}

# ------------------------------------------------------------------------------
# Optional IAM User & Least-Privilege Policy for Host Backup Script
# ------------------------------------------------------------------------------
resource "aws_iam_user" "backup" {
  count = var.create_s3_backup_iam_user ? 1 : 0
  name  = "${var.name_prefix}-backup-uploader"

  tags = {
    Name    = "${var.name_prefix}-backup-uploader"
    Purpose = "S3 backup uploader for Marginalia instance"
  }
}

resource "aws_iam_access_key" "backup" {
  count = var.create_s3_backup_iam_user ? 1 : 0
  user  = aws_iam_user.backup[0].name
}

resource "aws_iam_user_policy" "backup" {
  count = var.create_s3_backup_iam_user ? 1 : 0
  name  = "${var.name_prefix}-backup-uploader-policy"
  user  = aws_iam_user.backup[0].name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowBucketListing"
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetBucketLocation"
        ]
        Resource = aws_s3_bucket.backup.arn
      },
      {
        Sid    = "AllowBackupObjectOperations"
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject"
        ]
        Resource = "${aws_s3_bucket.backup.arn}/*"
      }
    ]
  })
}
