# ==============================================================================
# Marginalia — Core Lightsail Compute & Networking (Step 5.6.1)
#
# Provisions:
#   - SSH key pair (imported from a local public key)
#   - Debian 12 Lightsail instance bootstrapped with swap + Docker via cloud-init
#   - Static public IPv4 address attached to the instance
#   - Public firewall restricted to SSH (22), HTTP (80), HTTPS (443 TCP/UDP)
#
# Optional resources (S3 backup bucket, Route 53 record) live in separate files
# and are disabled by default.
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

locals {
  availability_zone = coalesce(var.availability_zone, "${var.aws_region}a")

  user_data = templatefile("${path.module}/templates/user-data.sh.tftpl", {
    swap_size_gb = var.swap_size_gb
    app_dir      = var.app_dir
    admin_user   = var.admin_user
  })
}

# ------------------------------------------------------------------------------
# SSH Key Pair
# ------------------------------------------------------------------------------
resource "aws_lightsail_key_pair" "marginalia" {
  name       = var.key_pair_name
  public_key = file(pathexpand(var.public_key_path))
}

# ------------------------------------------------------------------------------
# Compute Instance
# ------------------------------------------------------------------------------
resource "aws_lightsail_instance" "marginalia" {
  name              = var.instance_name
  availability_zone = local.availability_zone
  blueprint_id      = var.blueprint_id
  bundle_id         = var.bundle_id
  key_pair_name     = aws_lightsail_key_pair.marginalia.name

  # Runs once on first boot. Changing this script forces instance replacement,
  # so treat it as bootstrap-only and manage later changes over SSH.
  user_data = local.user_data
}

# ------------------------------------------------------------------------------
# Static IP (free while attached to a running instance)
# ------------------------------------------------------------------------------
resource "aws_lightsail_static_ip" "marginalia" {
  name = "${var.instance_name}-static-ip"
}

resource "aws_lightsail_static_ip_attachment" "marginalia" {
  static_ip_name = aws_lightsail_static_ip.marginalia.name
  instance_name  = aws_lightsail_instance.marginalia.name
}

# ------------------------------------------------------------------------------
# Public Firewall
# Replaces the Lightsail default rules entirely; anything not listed is blocked.
# ------------------------------------------------------------------------------
resource "aws_lightsail_instance_public_ports" "marginalia" {
  instance_name = aws_lightsail_instance.marginalia.name

  # SSH (restrict to your own IP range via var.ssh_allowed_cidrs)
  port_info {
    protocol  = "tcp"
    from_port = 22
    to_port   = 22
    cidrs     = var.ssh_allowed_cidrs
  }

  # HTTP (Let's Encrypt HTTP-01 challenge + redirect to HTTPS)
  port_info {
    protocol  = "tcp"
    from_port = 80
    to_port   = 80
    cidrs     = ["0.0.0.0/0"]
  }

  # HTTPS (HTTP/1.1 and HTTP/2)
  port_info {
    protocol  = "tcp"
    from_port = 443
    to_port   = 443
    cidrs     = ["0.0.0.0/0"]
  }

  # HTTPS (HTTP/3 over QUIC)
  port_info {
    protocol  = "udp"
    from_port = 443
    to_port   = 443
    cidrs     = ["0.0.0.0/0"]
  }
}
