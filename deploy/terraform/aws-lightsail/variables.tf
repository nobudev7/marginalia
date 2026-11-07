# ==============================================================================
# Marginalia — AWS Lightsail Input Variables (Compute & Networking)
# ==============================================================================

# ------------------------------------------------------------------------------
# Region & Placement
# ------------------------------------------------------------------------------
variable "aws_region" {
  description = "AWS region to deploy the Lightsail instance into. Note: Lightsail is not supported in us-west-1 (use us-west-2 for US West)."
  type        = string
  default     = "us-east-1"

  validation {
    condition = contains([
      "us-east-1",
      "us-east-2",
      "us-west-2",
      "ca-central-1",
      "eu-central-1",
      "eu-west-1",
      "eu-west-2",
      "eu-west-3",
      "eu-north-1",
      "ap-northeast-1",
      "ap-northeast-2",
      "ap-southeast-1",
      "ap-southeast-2",
      "ap-south-1"
    ], var.aws_region)
    error_message = "AWS Lightsail is not supported in region \"${var.aws_region}\". In the US, choose \"us-west-2\" (Oregon), \"us-east-1\" (N. Virginia), or \"us-east-2\" (Ohio)."
  }
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
