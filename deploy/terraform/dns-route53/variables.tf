# ==============================================================================
# Marginalia — Route 53 DNS Input Variables
# ==============================================================================

variable "aws_region" {
  description = "AWS region used by the AWS provider (Route 53 itself is global)."
  type        = string
  default     = "us-east-1"
}

variable "target_ip" {
  description = "Public static IPv4 address of the Marginalia server (e.g., static_ip output from ../aws-lightsail or ../oci)."
  type        = string

  validation {
    condition     = can(cidrhost("${var.target_ip}/32", 0))
    error_message = "target_ip must be a valid IPv4 address (e.g., \"203.0.113.10\")."
  }
}

variable "subdomain_name" {
  description = "Fully qualified domain name (e.g., \"reader.example.com\") for the Marginalia application."
  type        = string

  validation {
    condition     = length(trimspace(var.subdomain_name)) > 0
    error_message = "subdomain_name must not be empty."
  }
}

variable "route53_zone_id" {
  description = "Target Route 53 Hosted Zone ID (e.g., \"Z1234567890ABC\"). Specify either route53_zone_id or route53_zone_name."
  type        = string
  default     = null
}

variable "route53_zone_name" {
  description = "Domain name of the existing Route 53 hosted zone (e.g., \"example.com\"). Used to look up zone_id if route53_zone_id is omitted."
  type        = string
  default     = null
}

variable "route53_ttl" {
  description = "TTL in seconds for the Route 53 DNS A-record. Lower to 60 ahead of server migrations."
  type        = number
  default     = 300

  validation {
    condition     = var.route53_ttl >= 60 && var.route53_ttl <= 86400
    error_message = "route53_ttl must be between 60 and 86400 seconds."
  }
}

variable "tags" {
  description = "Additional tags applied to AWS resources."
  type        = map(string)
  default     = {}
}
