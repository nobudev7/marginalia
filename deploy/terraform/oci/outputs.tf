# ==============================================================================
# Marginalia — OCI Outputs
# Matches the ../aws-lightsail output interface so downstream workflows and
# ../dns-route53 consume `static_ip` identically.
# ==============================================================================

output "instance_name" {
  description = "Display name of the provisioned OCI compute instance."
  value       = oci_core_instance.marginalia.display_name
}

output "instance_ocid" {
  description = "OCID of the provisioned OCI compute instance."
  value       = oci_core_instance.marginalia.id
}

output "vcn_ocid" {
  description = "OCID of the provisioned OCI Virtual Cloud Network."
  value       = oci_core_vcn.marginalia.id
}

output "static_ip" {
  description = "The persistent Reserved Public IPv4 address assigned to Marginalia."
  value       = oci_core_public_ip.marginalia.ip_address
}

output "ssh_command" {
  description = "Direct SSH command to connect to the OCI instance."
  value       = "ssh -i ${var.public_key_path != null ? replace(var.public_key_path, ".pub", "") : "~/.ssh/id_ed25519"} ${var.admin_user}@${oci_core_public_ip.marginalia.ip_address}"
}

output "dns_a_record_target" {
  description = "Target public IPv4 address for DNS A-record configuration (e.g. ../dns-route53)."
  value       = oci_core_public_ip.marginalia.ip_address
}
