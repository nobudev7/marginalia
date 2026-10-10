# ==============================================================================
# Marginalia — Optional Route 53 DNS Subdomain Management (Step 5.6.3)
#
# Controlled by var.manage_route53_record (default: false).
#
# When enabled (true):
#   - Looks up the target Route 53 Hosted Zone by zone ID or zone name
#   - Creates an A record for the application subdomain (e.g., reader.example.com)
#   - Points traffic directly to the Lightsail static public IPv4 address
#
# When disabled (false):
#   - Zero Route 53 resources or data lookups are performed.
#   - Operators can point DNS manually in the Route 53 Console or via any
#     external DNS registrar (Cloudflare, Namecheap, etc.).
# ==============================================================================

# ------------------------------------------------------------------------------
# Route 53 Hosted Zone Lookup (Optional by Name)
# Used only when zone_id is not explicitly specified.
# ------------------------------------------------------------------------------
data "aws_route53_zone" "selected" {
  count = var.manage_route53_record && var.route53_zone_id == null && var.route53_zone_name != null ? 1 : 0

  name         = var.route53_zone_name
  private_zone = false
}

locals {
  route53_zone_id = var.manage_route53_record ? (
    var.route53_zone_id != null ? var.route53_zone_id : (
      length(data.aws_route53_zone.selected) > 0 ? data.aws_route53_zone.selected[0].zone_id : null
    )
  ) : null
}

# ------------------------------------------------------------------------------
# Subdomain A-Record
# ------------------------------------------------------------------------------
resource "aws_route53_record" "marginalia" {
  count = var.manage_route53_record ? 1 : 0

  zone_id = local.route53_zone_id
  name    = var.subdomain_name
  type    = "A"
  ttl     = var.route53_ttl
  records = [aws_lightsail_static_ip.marginalia.ip_address]
}
