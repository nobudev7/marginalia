# ==============================================================================
# Marginalia — OCI Virtual Cloud Network (VCN) & Public Firewall
#
# All VCN primitives below (VCN, Internet Gateway, Route Table, Security List,
# and Subnet) carry zero monthly charge on OCI.
# ==============================================================================

resource "oci_core_vcn" "marginalia" {
  compartment_id = local.compartment_ocid
  cidr_blocks    = [var.vcn_cidr]
  display_name   = "${var.instance_name}-vcn"
  dns_label      = "marginalia"
  freeform_tags  = local.freeform_tags
}

resource "oci_core_internet_gateway" "marginalia" {
  compartment_id = local.compartment_ocid
  vcn_id         = oci_core_vcn.marginalia.id
  display_name   = "${var.instance_name}-igw"
  enabled        = true
  freeform_tags  = local.freeform_tags
}

resource "oci_core_route_table" "public" {
  compartment_id = local.compartment_ocid
  vcn_id         = oci_core_vcn.marginalia.id
  display_name   = "${var.instance_name}-public-rt"
  freeform_tags  = local.freeform_tags

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.marginalia.id
  }
}

# ------------------------------------------------------------------------------
# Public Security List
# Restricts inbound traffic strictly to SSH (22), HTTP (80), HTTPS (443 TCP/UDP),
# and ICMP Path MTU Discovery. Note: OCI Ubuntu images also run host-level
# iptables, which templates/user-data.sh.tftpl configures on first boot.
# ------------------------------------------------------------------------------
resource "oci_core_security_list" "public" {
  compartment_id = local.compartment_ocid
  vcn_id         = oci_core_vcn.marginalia.id
  display_name   = "${var.instance_name}-public-sl"
  freeform_tags  = local.freeform_tags

  # Allow all outbound connections (RSS feed crawling, Let's Encrypt ACME, apt, S3)
  egress_security_rules {
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    protocol         = "all"
    stateless        = false
  }

  # SSH (TCP 22) — narrow via var.ssh_allowed_cidrs in production
  dynamic "ingress_security_rules" {
    for_each = var.ssh_allowed_cidrs
    content {
      protocol    = "6" # TCP
      source      = ingress_security_rules.value
      source_type = "CIDR_BLOCK"
      stateless   = false
      description = "SSH access"

      tcp_options {
        min = 22
        max = 22
      }
    }
  }

  # HTTP (TCP 80) — Let's Encrypt HTTP-01 challenge + HTTPS redirect
  ingress_security_rules {
    protocol    = "6" # TCP
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    stateless   = false
    description = "HTTP (ACME challenge and redirect)"

    tcp_options {
      min = 80
      max = 80
    }
  }

  # HTTPS (TCP 443) — HTTP/1.1 and HTTP/2
  ingress_security_rules {
    protocol    = "6" # TCP
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    stateless   = false
    description = "HTTPS (TCP)"

    tcp_options {
      min = 443
      max = 443
    }
  }

  # HTTPS (UDP 443) — HTTP/3 over QUIC
  ingress_security_rules {
    protocol    = "17" # UDP
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    stateless   = false
    description = "HTTPS HTTP/3 QUIC (UDP)"

    udp_options {
      min = 443
      max = 443
    }
  }

  # ICMP Type 3 Code 4 — Path MTU Discovery (recommended by OCI for public VCNs)
  ingress_security_rules {
    protocol    = "1" # ICMP
    source      = "0.0.0.0/0"
    source_type = "CIDR_BLOCK"
    stateless   = false
    description = "ICMP Path MTU Discovery"

    icmp_options {
      type = 3
      code = 4
    }
  }

  # Internal VCN ICMP Type 3 (Destination Unreachable)
  ingress_security_rules {
    protocol    = "1" # ICMP
    source      = var.vcn_cidr
    source_type = "CIDR_BLOCK"
    stateless   = false
    description = "Internal VCN ICMP"

    icmp_options {
      type = 3
    }
  }
}

resource "oci_core_subnet" "public" {
  compartment_id             = local.compartment_ocid
  vcn_id                     = oci_core_vcn.marginalia.id
  cidr_block                 = var.subnet_cidr
  display_name               = "${var.instance_name}-public-subnet"
  dns_label                  = "pub"
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.public.id]
  prohibit_public_ip_on_vnic = false
  freeform_tags              = local.freeform_tags
}
