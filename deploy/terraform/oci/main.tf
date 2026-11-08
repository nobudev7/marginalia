# ==============================================================================
# Marginalia — Oracle Cloud Infrastructure (OCI) Compute & Reserved IP Stack
#
# Supports BOTH OCI Always Free ($0/mo default) and OCI Paid shapes via tfvars.
#
# Provisions:
#   - Virtual Cloud Network (VCN), Internet Gateway, route table, subnet, and
#     security list restricted to SSH (22), HTTP (80), HTTPS (443 TCP/UDP)
#   - Canonical Ubuntu 24.04 instance (ARM64 or x86_64 matched to shape)
#     bootstrapped with swap, host iptables rules, fail2ban, and Docker
#   - Persistent Reserved Public IPv4 address ($0/mo when attached)
#
# Optional DNS routing (../dns-route53) and off-site S3 backup storage
# (../backup-s3) live in separate standalone stacks with independent lifecycles.
# ==============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = ">= 6.0.0, < 8.0.0"
    }
  }
}

provider "oci" {
  region              = var.oci_region
  tenancy_ocid        = var.tenancy_ocid
  user_ocid           = var.user_ocid
  fingerprint         = var.fingerprint
  private_key_path    = var.private_key_path != null ? pathexpand(var.private_key_path) : null
  config_file_profile = var.oci_config_file_profile
}

# ------------------------------------------------------------------------------
# Availability Domains & Dynamic OS Image Lookup
# ------------------------------------------------------------------------------
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

# Selects the latest standard Canonical Ubuntu image matching var.instance_shape
# (automatically picks aarch64 for A1.Flex/A2.Flex and x86_64 for E2/E5 shapes,
# excluding stripped -Minimal- variants so Docker kernel modules are present).
data "oci_core_images" "ubuntu" {
  count = var.image_ocid == null ? 1 : 0

  compartment_id           = local.compartment_ocid
  operating_system         = var.operating_system
  operating_system_version = var.operating_system_version
  shape                    = var.instance_shape
  state                    = "AVAILABLE"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"

  filter {
    name   = "display_name"
    values = ["^Canonical-Ubuntu-[0-9]+\\.[0-9]+-(aarch64-)?20[0-9]{2}\\..*$"]
    regex  = true
  }
}

locals {
  compartment_ocid = coalesce(var.compartment_ocid, var.tenancy_ocid)

  availability_domain = coalesce(
    var.availability_domain,
    data.oci_identity_availability_domains.ads.availability_domains[
      min(
        var.availability_domain_number - 1,
        length(data.oci_identity_availability_domains.ads.availability_domains) - 1,
      )
    ].name,
  )

  image_ocid = var.image_ocid != null ? var.image_ocid : data.oci_core_images.ubuntu[0].images[0].id

  freeform_tags = merge(
    {
      Project   = "marginalia"
      ManagedBy = "terraform"
    },
    var.tags,
  )

  user_data = templatefile("${path.module}/templates/user-data.sh.tftpl", {
    swap_size_gb = var.swap_size_gb
    app_dir      = var.app_dir
    admin_user   = var.admin_user
  })
}

# ------------------------------------------------------------------------------
# Compute Instance
# ------------------------------------------------------------------------------
resource "oci_core_instance" "marginalia" {
  availability_domain = local.availability_domain
  compartment_id      = local.compartment_ocid
  display_name        = var.instance_name
  shape               = var.instance_shape

  dynamic "shape_config" {
    for_each = endswith(var.instance_shape, ".Flex") ? [1] : []
    content {
      ocpus         = var.instance_ocpus
      memory_in_gbs = var.instance_memory_gb
    }
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    display_name     = "${var.instance_name}-vnic"
    hostname_label   = var.hostname_label
    assign_public_ip = false
  }

  source_details {
    source_type             = "image"
    source_id               = local.image_ocid
    boot_volume_size_in_gbs = var.boot_volume_size_gb
  }

  metadata = {
    ssh_authorized_keys = trimspace(file(pathexpand(var.public_key_path)))
    user_data           = base64encode(local.user_data)
  }

  freeform_tags = local.freeform_tags

  # Prevent monthly Oracle Ubuntu image refreshes or bootstrap comments from
  # forcing accidental VM/boot-volume replacement on future `terraform apply`.
  lifecycle {
    ignore_changes = [
      source_details[0].source_id,
      metadata["user_data"],
    ]
  }
}

# ------------------------------------------------------------------------------
# Reserved Static Public IPv4 ($0.00/mo on OCI when attached)
# ------------------------------------------------------------------------------
data "oci_core_vnic_attachments" "marginalia" {
  compartment_id      = local.compartment_ocid
  availability_domain = oci_core_instance.marginalia.availability_domain
  instance_id         = oci_core_instance.marginalia.id
}

data "oci_core_private_ips" "marginalia" {
  vnic_id = data.oci_core_vnic_attachments.marginalia.vnic_attachments[0].vnic_id
}

resource "oci_core_public_ip" "marginalia" {
  compartment_id = local.compartment_ocid
  display_name   = "${var.instance_name}-static-ip"
  lifetime       = "RESERVED"
  private_ip_id  = data.oci_core_private_ips.marginalia.private_ips[0].id
  freeform_tags  = local.freeform_tags
}
