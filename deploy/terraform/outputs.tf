# ==============================================================================
# Marginalia — Terraform Outputs (Step 5.6.4)
# Actionable connection details, IP addresses, DNS targets, and credentials.
# ==============================================================================

# ------------------------------------------------------------------------------
# Core Compute & Connection Details
# ------------------------------------------------------------------------------
output "instance_name" {
  description = "Name of the provisioned Lightsail instance."
  value       = aws_lightsail_instance.marginalia.name
}

output "static_ip" {
  description = "The persistent public static IPv4 address assigned to Marginalia."
  value       = aws_lightsail_static_ip.marginalia.ip_address
}

output "ssh_command" {
  description = "Direct SSH command to connect to the Lightsail instance."
  value       = "ssh -i ${var.public_key_path != null ? replace(var.public_key_path, ".pub", "") : "~/.ssh/id_ed25519"} ${var.admin_user}@${aws_lightsail_static_ip.marginalia.ip_address}"
}

# ------------------------------------------------------------------------------
# Domain & Web Access
# ------------------------------------------------------------------------------
output "dns_a_record_target" {
  description = "Target public IPv4 address for manual DNS A-record configuration."
  value       = aws_lightsail_static_ip.marginalia.ip_address
}

output "app_domain" {
  description = "Configured application domain name (or null if unset)."
  value       = var.subdomain_name
}

output "app_url" {
  description = "Primary HTTPS URL to access the Marginalia web application."
  value       = var.subdomain_name != null ? "https://${var.subdomain_name}" : "https://${aws_lightsail_static_ip.marginalia.ip_address}"
}

output "route53_record_fqdn" {
  description = "Fully qualified domain name of the Route 53 A-record (if managed by Terraform)."
  value       = var.manage_route53_record ? aws_route53_record.marginalia[0].fqdn : null
}

# ------------------------------------------------------------------------------
# Optional S3 Backup Storage
# ------------------------------------------------------------------------------
output "s3_backup_bucket_name" {
  description = "Name of the provisioned S3 backup bucket (if enabled)."
  value       = var.enable_s3_backup_bucket ? aws_s3_bucket.backup[0].id : null
}

output "s3_backup_bucket_uri" {
  description = "S3 URI to configure for off-site backups in deploy/.env (if enabled)."
  value       = var.enable_s3_backup_bucket ? "s3://${aws_s3_bucket.backup[0].id}/marginalia" : null
}

output "s3_backup_iam_access_key_id" {
  description = "AWS Access Key ID for the dedicated backup uploader IAM user (if enabled)."
  value       = var.enable_s3_backup_bucket && var.create_s3_backup_iam_user ? aws_iam_access_key.backup[0].id : null
}

output "s3_backup_iam_secret_access_key" {
  description = "AWS Secret Access Key for the dedicated backup uploader IAM user (if enabled)."
  value       = var.enable_s3_backup_bucket && var.create_s3_backup_iam_user ? aws_iam_access_key.backup[0].secret : null
  sensitive   = true
}
