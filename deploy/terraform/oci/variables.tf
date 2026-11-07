# ==============================================================================
# Marginalia — OCI Input Variables (Always Free Defaults + Paid Scalability)
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. OCI Authentication & Placement
# ------------------------------------------------------------------------------
variable "oci_region" {
  description = "OCI region identifier (e.g., \"us-sanjose-1\", \"us-phoenix-1\", \"us-ashburn-1\", \"ap-tokyo-1\"). Always Free resources must be deployed in your tenancy's Home Region."
  type        = string
}

variable "tenancy_ocid" {
  description = "OCID of your OCI tenancy (starts with \"ocid1.tenancy.oc1..\")."
  type        = string

  validation {
    condition     = startswith(var.tenancy_ocid, "ocid1.tenancy.")
    error_message = "tenancy_ocid must be a valid OCI tenancy OCID starting with \"ocid1.tenancy.\"."
  }
}

variable "compartment_ocid" {
  description = "OCID of the compartment to deploy resources into. Defaults to tenancy_ocid (root compartment) when null."
  type        = string
  default     = null
}

variable "user_ocid" {
  description = "OCID of the OCI user calling the API. Leave null to read from ~/.oci/config."
  type        = string
  default     = null
}

variable "fingerprint" {
  description = "Fingerprint of the OCI API signing public key. Leave null to read from ~/.oci/config."
  type        = string
  default     = null
}

variable "private_key_path" {
  description = "Path to the private PEM key matching fingerprint (e.g., \"~/.oci/oci_api_key.pem\"). Leave null to read from ~/.oci/config."
  type        = string
  default     = null
}

variable "oci_config_file_profile" {
  description = "Named profile in ~/.oci/config (e.g., \"DEFAULT\"). Leave null when passing explicit API key variables or using default SDK resolution."
  type        = string
  default     = null
}

variable "availability_domain" {
  description = "Explicit OCI Availability Domain name (e.g., \"Uocm:PHX-AD-1\"). When null, availability_domain_number selects automatically."
  type        = string
  default     = null
}

variable "availability_domain_number" {
  description = "1-based Availability Domain index (1, 2, or 3) when availability_domain is null. Try 2 or 3 in multi-AD regions if AD-1 reports out-of-host-capacity."
  type        = number
  default     = 1

  validation {
    condition     = var.availability_domain_number >= 1 && var.availability_domain_number <= 3 && floor(var.availability_domain_number) == var.availability_domain_number
    error_message = "availability_domain_number must be 1, 2, or 3."
  }
}

# ------------------------------------------------------------------------------
# 2. Compute Shape & Sizing (Defaults stay well within OCI Always Free limits)
# ------------------------------------------------------------------------------
variable "instance_name" {
  description = "Display name of the OCI compute instance and prefix for VCN/IP resources."
  type        = string
  default     = "marginalia"
}

variable "hostname_label" {
  description = "DNS hostname label for the primary VNIC inside the VCN (alphanumeric, max 63 chars)."
  type        = string
  default     = "marginalia"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,62}$", var.hostname_label))
    error_message = "hostname_label must start with a lowercase letter and contain only lowercase letters, digits, or hyphens."
  }
}

variable "instance_shape" {
  description = <<-EOT
    OCI compute shape. Common options:
      Always Free eligible:
        - "VM.Standard.A1.Flex"    : Ampere Altra ARM64 (Always Free up to 4 OCPUs + 24 GB RAM across tenancy)
        - "VM.Standard.E2.1.Micro" : AMD x86_64 fixed shape (1/8 OCPU, 1 GB RAM — tight for Java+MySQL; use swap)
      Paid / Scaled-Up options:
        - "VM.Standard.A1.Flex"    : Scales past Always Free allowance seamlessly on Pay-As-You-Go
        - "VM.Standard.A2.Flex"    : Next-gen Ampere ARM64 Flex shape
        - "VM.Standard.E5.Flex"    : AMD EPYC x86_64 Flex shape
  EOT
  type        = string
  default     = "VM.Standard.A1.Flex"
}

variable "instance_ocpus" {
  description = "Number of OCPUs for .Flex shapes. Always Free A1.Flex includes up to 4 OCPUs total across your tenancy."
  type        = number
  default     = 2

  validation {
    condition     = var.instance_ocpus >= 1 && var.instance_ocpus <= 64
    error_message = "instance_ocpus must be between 1 and 64."
  }
}

variable "instance_memory_gb" {
  description = "Memory in GB for .Flex shapes. Always Free A1.Flex includes up to 24 GB RAM total across your tenancy."
  type        = number
  default     = 12

  validation {
    condition     = var.instance_memory_gb >= 1 && var.instance_memory_gb <= 512
    error_message = "instance_memory_gb must be between 1 and 512."
  }
}

variable "boot_volume_size_gb" {
  description = "Boot volume size in GB (minimum 50 GB on OCI). Always Free includes up to 200 GB total Block/Boot Volume storage across your tenancy."
  type        = number
  default     = 50

  validation {
    condition     = var.boot_volume_size_gb >= 50 && var.boot_volume_size_gb <= 2000 && floor(var.boot_volume_size_gb) == var.boot_volume_size_gb
    error_message = "boot_volume_size_gb must be a whole number >= 50."
  }
}

# ------------------------------------------------------------------------------
# 3. Operating System & Bootstrap
# ------------------------------------------------------------------------------
variable "operating_system" {
  description = "OCI platform image OS family used when image_ocid is null."
  type        = string
  default     = "Canonical Ubuntu"
}

variable "operating_system_version" {
  description = "OCI platform image OS version used when image_ocid is null."
  type        = string
  default     = "24.04"
}

variable "image_ocid" {
  description = "Optional explicit OCI image OCID override. Leave null to auto-select the latest Canonical Ubuntu 24.04 image matching instance_shape."
  type        = string
  default     = null
}

variable "admin_user" {
  description = "Default SSH login user for the chosen OS image (\"ubuntu\" on Canonical Ubuntu)."
  type        = string
  default     = "ubuntu"

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
# 4. SSH Access & Networking
# ------------------------------------------------------------------------------
variable "public_key_path" {
  description = "Path to the local SSH public key injected into the instance."
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "ssh_allowed_cidrs" {
  description = "CIDR ranges allowed to reach SSH (port 22). Narrow this to your own IP (e.g., [\"203.0.113.10/32\"])."
  type        = list(string)
  default     = ["0.0.0.0/0"]

  validation {
    condition     = length(var.ssh_allowed_cidrs) > 0 && alltrue([for c in var.ssh_allowed_cidrs : can(cidrhost(c, 0))])
    error_message = "ssh_allowed_cidrs must contain at least one valid IPv4 CIDR block."
  }
}

variable "vcn_cidr" {
  description = "IPv4 CIDR block for the OCI Virtual Cloud Network."
  type        = string
  default     = "10.0.0.0/16"

  validation {
    condition     = can(cidrhost(var.vcn_cidr, 0))
    error_message = "vcn_cidr must be a valid IPv4 CIDR block."
  }
}

variable "subnet_cidr" {
  description = "IPv4 CIDR block for the public subnet inside the VCN."
  type        = string
  default     = "10.0.1.0/24"

  validation {
    condition     = can(cidrhost(var.subnet_cidr, 0))
    error_message = "subnet_cidr must be a valid IPv4 CIDR block."
  }
}

# ------------------------------------------------------------------------------
# 5. Tagging
# ------------------------------------------------------------------------------
variable "tags" {
  description = "Additional freeform tags applied to all OCI resources."
  type        = map(string)
  default     = {}
}
