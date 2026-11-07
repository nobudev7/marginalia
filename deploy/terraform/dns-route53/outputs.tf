# ==============================================================================
# Marginalia — Route 53 DNS Outputs
# ==============================================================================

output "route53_record_fqdn" {
  description = "Fully qualified domain name of the managed Route 53 A-record."
  value       = aws_route53_record.marginalia.fqdn
}

output "app_domain" {
  description = "Configured application domain name."
  value       = var.subdomain_name
}

output "app_url" {
  description = "Primary HTTPS URL to access the Marginalia web application."
  value       = "https://${var.subdomain_name}"
}

output "target_ip" {
  description = "IPv4 address currently targeted by the Route 53 A-record."
  value       = var.target_ip
}
