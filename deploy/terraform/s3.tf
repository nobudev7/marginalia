# ==============================================================================
# Marginalia — Optional S3 Off-Site Backup Storage (Step 5.6.2)
#
# Controlled by var.enable_s3_backup_bucket (default: false).
#
# When enabled (true):
#   - Provisions a private, dedicated S3 bucket
#   - Enforces default AES256 server-side encryption (SSE-S3)
#   - Enforces public access block (blocks all public ACLs and policies)
#   - Configures lifecycle expiration rule for archives older than var.s3_backup_retention_days
#   - Optionally provisions a dedicated, least-privilege IAM user for backup uploads
#
# When disabled (false):
#   - Zero S3 or IAM resources are created.
#   - Operators can manage backups locally (7-day retention) or supply an existing S3 bucket.
# ==============================================================================

# ------------------------------------------------------------------------------
# S3 Bucket
# ------------------------------------------------------------------------------
resource "aws_s3_bucket" "backup" {
  count = var.enable_s3_backup_bucket ? 1 : 0

  bucket        = var.s3_bucket_name
  bucket_prefix = var.s3_bucket_name == null ? var.s3_bucket_prefix : null
  force_destroy = var.s3_force_destroy

  tags = {
    Name    = "${var.instance_name}-backup-bucket"
    Purpose = "Database disaster recovery backups"
  }
}

# ------------------------------------------------------------------------------
# Server-Side Encryption (SSE-S3 / AES256)
# ------------------------------------------------------------------------------
resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  count  = var.enable_s3_backup_bucket ? 1 : 0
  bucket = aws_s3_bucket.backup[0].id

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
  count  = var.enable_s3_backup_bucket ? 1 : 0
  bucket = aws_s3_bucket.backup[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ------------------------------------------------------------------------------
# Bucket Lifecycle Policy (Automated Cloud Retention Rotation)
# ------------------------------------------------------------------------------
resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  count  = var.enable_s3_backup_bucket && var.s3_backup_retention_days > 0 ? 1 : 0
  bucket = aws_s3_bucket.backup[0].id

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
  count = var.enable_s3_backup_bucket && var.create_s3_backup_iam_user ? 1 : 0
  name  = "${var.instance_name}-backup-uploader"

  tags = {
    Name    = "${var.instance_name}-backup-uploader"
    Purpose = "S3 backup uploader for Lightsail instance"
  }
}

resource "aws_iam_access_key" "backup" {
  count = var.enable_s3_backup_bucket && var.create_s3_backup_iam_user ? 1 : 0
  user  = aws_iam_user.backup[0].name
}

resource "aws_iam_user_policy" "backup" {
  count = var.enable_s3_backup_bucket && var.create_s3_backup_iam_user ? 1 : 0
  name  = "${var.instance_name}-backup-uploader-policy"
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
        Resource = aws_s3_bucket.backup[0].arn
      },
      {
        Sid    = "AllowBackupObjectOperations"
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject"
        ]
        Resource = "${aws_s3_bucket.backup[0].arn}/*"
      }
    ]
  })
}
