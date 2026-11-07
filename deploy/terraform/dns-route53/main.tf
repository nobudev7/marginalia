# ==============================================================================
# Marginalia — Standalone AWS Route 53 Subdomain DNS Stack
#
# Maps an application subdomain (e.g., reader.example.com) to any compute
# provider's static IPv4 address (AWS Lightsail, Oracle Cloud, etc.).
#
# Separating DNS from compute lets you switch servers (e.g., Lightsail -> OCI)
# by updating a single variable (`target_ip`) without recreating DNS records
# or risking accidental record deletion during `terraform destroy` on a VM.
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
# Route 53 Hosted Zone Lookup (Optional by Name)
# Used when route53_zone_id is not explicitly specified.
# ------------------------------------------------------------------------------
data "aws_route53_zone" "selected" {
  count = var.route53_zone_id == null && var.route53_zone_name != null ? 1 : 0

  name         = var.route53_zone_name
  private_zone = false
}

locals {
  route53_zone_id = var.route53_zone_id != null ? var.route53_zone_id : (
    length(data.aws_route53_zone.selected) > 0 ? data.aws_route53_zone.selected[0].zone_id : null
  )
}

# ------------------------------------------------------------------------------
# Subdomain A-Record
# ------------------------------------------------------------------------------
resource "aws_route53_record" "marginalia" {
  zone_id = local.route53_zone_id
  name    = var.subdomain_name
  type    = "A"
  ttl     = var.route53_ttl
  records = [var.target_ip]

  lifecycle {
    precondition {
      condition     = local.route53_zone_id != null
      error_message = "Specify either route53_zone_id or route53_zone_name in terraform.tfvars."
    }
  }
}
