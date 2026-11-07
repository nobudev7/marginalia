# ==============================================================================
# Marginalia — S3 Off-Site Backup Storage Outputs
# ==============================================================================

output "s3_backup_bucket_name" {
  description = "Name of the provisioned S3 backup bucket."
  value       = aws_s3_bucket.backup.id
}

output "s3_backup_bucket_uri" {
  description = "S3 URI to configure as S3_BACKUP_URI in deploy/.env."
  value       = "s3://${aws_s3_bucket.backup.id}/marginalia"
}

output "s3_backup_iam_access_key_id" {
  description = "AWS Access Key ID for the dedicated backup uploader IAM user (if enabled)."
  value       = var.create_s3_backup_iam_user ? aws_iam_access_key.backup[0].id : null
}

output "s3_backup_iam_secret_access_key" {
  description = "AWS Secret Access Key for the dedicated backup uploader IAM user (if enabled)."
  value       = var.create_s3_backup_iam_user ? aws_iam_access_key.backup[0].secret : null
  sensitive   = true
}
