# ==============================================================================
# Marginalia — AWS Lightsail Outputs
# ==============================================================================

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

output "dns_a_record_target" {
  description = "Target public IPv4 address for DNS A-record configuration (e.g. ../dns-route53)."
  value       = aws_lightsail_static_ip.marginalia.ip_address
}
