# ==============================================================================
# Marginalia — Input Variables (Core Compute & Networking)
# ==============================================================================

# ------------------------------------------------------------------------------
# Region & Placement
# ------------------------------------------------------------------------------
variable "aws_region" {
  description = "AWS region to deploy the Lightsail instance into."
  type        = string
  default     = "us-east-1"
}

variable "availability_zone" {
  description = "Lightsail availability zone. Defaults to \"<aws_region>a\" when null."
  type        = string
  default     = null
}

# ------------------------------------------------------------------------------
# Instance
# ------------------------------------------------------------------------------
variable "instance_name" {
  description = "Name of the Lightsail instance (also used as a prefix for related resources)."
  type        = string
  default     = "marginalia"
}

variable "blueprint_id" {
  description = "Lightsail OS blueprint. List options with: aws lightsail get-blueprints"
  type        = string
  default     = "debian_12"
}

variable "bundle_id" {
  description = <<-EOT
    Lightsail bundle (instance size). Common Linux options (IPv4 included):
      nano_3_0  - 512 MB RAM  (not recommended for this stack)
      micro_3_0 - 1 GB RAM    (viable with swap; budget option)
      small_3_0 - 2 GB RAM    (comfortable headroom)
    List current bundles and prices with: aws lightsail get-bundles
  EOT
  type        = string
  default     = "micro_3_0"
}

variable "admin_user" {
  description = "Default SSH login user for the chosen blueprint (\"admin\" on Debian)."
  type        = string
  default     = "admin"

  validation {
    condition     = can(regex("^[a-z_][a-z0-9_-]*$", var.admin_user))
    error_message = "admin_user must be a valid Linux username."
  }
}

variable "app_dir" {
  description = "Directory on the host where the Marginalia repository will be cloned."
  type        = string
  default     = "/opt/marginalia"
}

variable "swap_size_gb" {
  description = "Swap file size in GB created on first boot (0 disables swap creation)."
  type        = number
  default     = 2

  validation {
    condition     = var.swap_size_gb >= 0 && var.swap_size_gb <= 8 && floor(var.swap_size_gb) == var.swap_size_gb
    error_message = "swap_size_gb must be a whole number between 0 and 8."
  }
}

# ------------------------------------------------------------------------------
# SSH Access
# ------------------------------------------------------------------------------
variable "key_pair_name" {
  description = "Name of the Lightsail key pair created from the local public key."
  type        = string
  default     = "marginalia-key"
}

variable "public_key_path" {
  description = "Path to the local SSH public key to import into Lightsail."
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "ssh_allowed_cidrs" {
  description = "CIDR ranges allowed to reach SSH (port 22). Narrow this to your own IP (e.g. [\"203.0.113.10/32\"])."
  type        = list(string)
  default     = ["0.0.0.0/0"]

  validation {
    condition     = length(var.ssh_allowed_cidrs) > 0 && alltrue([for c in var.ssh_allowed_cidrs : can(cidrhost(c, 0))])
    error_message = "ssh_allowed_cidrs must contain at least one valid IPv4 CIDR block."
  }
}

# ------------------------------------------------------------------------------
# Tagging
# ------------------------------------------------------------------------------
variable "tags" {
  description = "Additional tags applied to all resources."
  type        = map(string)
  default     = {}
}

# ------------------------------------------------------------------------------
# Optional S3 Backup Storage (Step 5.6.2)
# ------------------------------------------------------------------------------
variable "enable_s3_backup_bucket" {
  description = "Whether to provision a dedicated AWS S3 bucket for off-site database backups."
  type        = bool
  default     = false
}

variable "s3_bucket_name" {
  description = "Explicit name for the S3 backup bucket. If null and enable_s3_backup_bucket is true, s3_bucket_prefix is used."
  type        = string
  default     = null
}

variable "s3_bucket_prefix" {
  description = "Prefix for the S3 backup bucket name if s3_bucket_name is not specified."
  type        = string
  default     = "marginalia-backup-"
}

variable "s3_backup_retention_days" {
  description = "Number of days after which backup archives in S3 are automatically deleted (0 disables lifecycle expiration)."
  type        = number
  default     = 90

  validation {
    condition     = var.s3_backup_retention_days >= 0
    error_message = "s3_backup_retention_days must be 0 or a positive integer."
  }
}

variable "s3_force_destroy" {
  description = "Allow destroying the S3 bucket even if it contains objects. Keep false for production safety."
  type        = bool
  default     = false
}

variable "create_s3_backup_iam_user" {
  description = "Whether to create a dedicated IAM user and policy with restricted access to the S3 backup bucket."
  type        = bool
  default     = false
}

# ------------------------------------------------------------------------------
# Optional Route 53 Subdomain DNS Management (Step 5.6.3)
# ------------------------------------------------------------------------------
variable "manage_route53_record" {
  description = "Whether to automate Route 53 DNS A-record creation for the application subdomain."
  type        = bool
  default     = false
}

variable "subdomain_name" {
  description = "Fully qualified domain name (e.g., 'reader.example.com') or subdomain prefix for the Marginalia application."
  type        = string
  default     = null
}

variable "route53_zone_id" {
  description = "Target Route 53 Hosted Zone ID (e.g., 'Z1234567890ABC'). Specify either route53_zone_id or route53_zone_name when manage_route53_record is true."
  type        = string
  default     = null
}

variable "route53_zone_name" {
  description = "Domain name of the existing Route 53 hosted zone (e.g., 'example.com'). Used to look up zone_id if route53_zone_id is omitted."
  type        = string
  default     = null
}

variable "route53_ttl" {
  description = "TTL in seconds for the Route 53 DNS A-record."
  type        = number
  default     = 300

  validation {
    condition     = var.route53_ttl >= 60 && var.route53_ttl <= 86400
    error_message = "route53_ttl must be between 60 and 86400 seconds."
  }
}


