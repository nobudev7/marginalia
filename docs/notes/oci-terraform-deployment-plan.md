# Multi-Provider & Modular Terraform Deployment Plan (AWS Lightsail + OCI)

> **Status:** Completed & Live in Production on OCI  
> **Target Layout:** `deploy/terraform/{aws-lightsail, oci, dns-route53, backup-s3}/`  
> **Rollout Milestones:**  
> 1. **Stage 1 (Completed):** Split existing Terraform into `aws-lightsail/`, `dns-route53/`, and `backup-s3/` with **zero changes to live AWS resources**.  
> 2. **Stage 2 (Completed):** Added pure `deploy/terraform/oci/` (supporting both `$0/mo` Always Free and Paid shapes via `tfvars`) and updated deployment documentation (PR #32).  
> 3. **Stage 3 (Live Cutover Completed):** Provisioned OCI compute (`VM.Standard.A1.Flex`), migrated database backup, switched `target_ip` in `dns-route53/`, verified automated Let's Encrypt TLS issuance and daily `marginalia-backup.timer`. (Lightsail VM retained temporarily as rollback safety net).

---

## 1. Architectural Principles

1. **Decoupled Lifecycles (Compute vs. Routing vs. Backups):**
   - **Compute (`aws-lightsail/` or `oci/`)** is replaceable infrastructure that outputs a `static_ip`.
   - **DNS (`dns-route53/`)** is a lightweight routing pointer (`subdomain` &rarr; `target_ip`). Users on Cloudflare, Namecheap, Porkbun, etc. simply skip `dns-route53/` and point their `A` record manually to `static_ip`.
   - **Backup Storage (`backup-s3/`)** is a long-lived disaster-recovery asset (90-day encrypted database dumps) that survives server migrations and compute teardowns untouched.
2. **Pure Provider Isolation:**
   - `deploy/terraform/oci/` depends **exclusively** on `oracle/oci`—no `hashicorp/aws` plugin bloat, no dummy AWS credential workarounds, and no AWS variables cluttering `oci/variables.tf`.
   - `deploy/terraform/aws-lightsail/` depends exclusively on `hashicorp/aws` for compute, static IP, and firewall.
3. **Single OCI Stack for Always Free & Paid Tiers:**
   - On OCI, Always Free and Paid tiers use identical resources (`oci_core_vcn`, `oci_core_instance`, `oci_core_public_ip`). `deploy/terraform/oci/` defaults to Always Free limits (`VM.Standard.A1.Flex`, 2 OCPUs, 12 GB RAM, 50 GB boot volume) and scales to Paid ARM (`A1.Flex`, `A2.Flex`) or x86 (`E5.Flex`) shapes via `terraform.tfvars`.
4. **Zero Application or Container Changes:**
   - Shared runtime files (`deploy/compose.yaml`, `Caddyfile`, `mysql.cnf`, `.env.example`, `backup.sh`, `restore.sh`) remain 100% unchanged. All Docker base images publish official `linux/amd64` and `linux/arm64` manifests.
5. **Additive Extensibility:**
   - Future offerings—such as `backup-oci-storage/` (OCI's 20 GB Always Free S3-compatible Object Storage) or `dns-cloudflare/` / `dns-oci/`—can be added as sibling directories without modifying existing stacks.

---

## 2. Target Directory Structure

```text
deploy/
├── compose.yaml                      # Unchanged — multi-arch (amd64 + arm64)
├── Caddyfile                         # Unchanged — auto Let's Encrypt TLS
├── mysql.cnf                         # Unchanged
├── .env.example                      # Unchanged
├── backup.sh                         # Unchanged — local 7-day + optional S3 sync
├── restore.sh                        # Unchanged
└── terraform/
    ├── README.md                     # Architecture overview, stack matrix & quickstart
    │
    ├── aws-lightsail/                # Compute Option 1: AWS Lightsail (~$7/mo)
    │   ├── main.tf                   # Key pair, Debian 12 instance, static IP, ports
    │   ├── variables.tf              # Compute & SSH variables only
    │   ├── outputs.tf                # instance_name, static_ip, ssh_command, dns_a_record_target
    │   ├── terraform.tfvars.example
    │   └── templates/
    │       └── user-data.sh.tftpl    # Unchanged byte-for-byte (prevents drift on live VM)
    │
    ├── oci/                          # Compute Option 2: Oracle Cloud ($0 Always Free or Paid)
    │   ├── main.tf                   # OCI provider, dynamic OS image, VM, Reserved Public IP
    │   ├── network.tf                # VCN, Internet Gateway, route table, subnet, security list
    │   ├── variables.tf              # Always Free defaults + Paid shape flexibility
    │   ├── outputs.tf                # Same output interface: instance_name, static_ip, ssh_command...
    │   ├── terraform.tfvars.example  # Annotated examples for Free Tier, PAYG, and Paid
    │   └── templates/
    │       └── user-data.sh.tftpl    # Ubuntu 24.04 bootstrap + OCI host iptables rules
    │
    ├── dns-route53/                  # Optional Routing: AWS Route 53 Subdomain A-Record
    │   ├── main.tf                   # Zone lookup + aws_route53_record -> var.target_ip
    │   ├── variables.tf              # target_ip, subdomain_name, zone_id/name, route53_ttl
    │   ├── outputs.tf                # route53_record_fqdn, app_domain, app_url, target_ip
    │   └── terraform.tfvars.example
    │
    └── backup-s3/                    # Optional Off-Site Backups: AWS S3 Bucket + IAM
        ├── main.tf                   # Private S3 bucket, SSE-S3, public block, lifecycle, optional IAM
        ├── variables.tf              # s3_bucket_name/prefix, retention_days, create_iam_user
        ├── outputs.tf                # s3_backup_bucket_name, s3_backup_bucket_uri, IAM keys
        └── terraform.tfvars.example
```

---

## 3. Stack Specifications

### 3.1 `deploy/terraform/aws-lightsail/` (Compute Option 1)
- **Resources:** `aws_lightsail_key_pair.marginalia`, `aws_lightsail_instance.marginalia`, `aws_lightsail_static_ip.marginalia`, `aws_lightsail_static_ip_attachment.marginalia`, `aws_lightsail_instance_public_ports.marginalia`.
- **Drift Guarantee:** Keeps resource names, `default_tags`, and `templates/user-data.sh.tftpl` byte-for-byte identical to the current configuration so the running Lightsail instance has zero diff.
- **Outputs:** `instance_name`, `static_ip`, `ssh_command`, `dns_a_record_target`.

### 3.2 `deploy/terraform/dns-route53/` (Optional Route 53 Routing)
- **Purpose:** Maps `var.subdomain_name` (e.g., `reader.example.com`) to any compute provider's `var.target_ip` (`aws_lightsail`, `oci`, or any VPS).
- **Resources:** Optional `data.aws_route53_zone.selected` (when looked up by `route53_zone_name`) + `aws_route53_record.marginalia`.
- **Key Variables:**
  - `target_ip` *(required, validated IPv4 string)* — populated from `aws-lightsail` or `oci` `static_ip` output (either set in `terraform.tfvars` or passed via `-var="target_ip=$(terraform -chdir=../oci output -raw static_ip)"`).
  - `subdomain_name` *(required string)*
  - `route53_zone_id` / `route53_zone_name`
  - `route53_ttl` *(default `300`, lowered to `60` before migrations)*
  - `aws_region` *(default `"us-east-1"`)*
- **Outputs:** `route53_record_fqdn`, `app_domain`, `app_url`, `target_ip`.

### 3.3 `deploy/terraform/backup-s3/` (Optional AWS S3 Off-Site Backups)
- **Purpose:** Provisions an encrypted, private S3 bucket with automated expiration (`s3_backup_retention_days`, default `90`) and an optional least-privilege IAM uploader user. Works identically whether Marginalia runs on AWS Lightsail or OCI (providing cross-cloud disaster recovery when paired with OCI).
- **Resources:** `aws_s3_bucket.backup`, `aws_s3_bucket_server_side_encryption_configuration.backup`, `aws_s3_bucket_public_access_block.backup`, `aws_s3_bucket_lifecycle_configuration.backup[0]`, and optional `aws_iam_user.backup[0]` / `aws_iam_access_key.backup[0]` / `aws_iam_user_policy.backup[0]`.
- **Tag Compatibility:** Preserves `name_prefix = "marginalia"` (`Name = "marginalia-backup-bucket"`, `Purpose = "Database disaster recovery backups"`) and `default_tags` (`Project = "marginalia"`, `ManagedBy = "terraform"` + `var.tags`) so existing buckets experience zero tag modifications.

### 3.4 `deploy/terraform/oci/` (Compute Option 2 — Always Free & Paid)

#### Provider & Authentication (`main.tf`)
- Uses **only** `oracle/oci` (`>= 6.0, < 8.0`).
- Supports standard `~/.oci/config` profile authentication (`oci_config_file_profile = "DEFAULT"`) as well as explicit API key variables (`tenancy_ocid`, `user_ocid`, `fingerprint`, `private_key_path`, `oci_region`).
- `.gitignore` updated with `*.pem` so OCI API signing keys can never be accidentally committed.

#### Networking (`network.tf` — All `$0.00/mo`)
- `oci_core_vcn.marginalia` (`10.0.0.0/16`, DNS label `marginalia`)
- `oci_core_internet_gateway.marginalia` + `oci_core_route_table.public` (`0.0.0.0/0` &rarr; IGW)
- `oci_core_security_list.public`:
  - **Ingress:** TCP `22` (`var.ssh_allowed_cidrs`), TCP `80` (`0.0.0.0/0`), TCP `443` (`0.0.0.0/0`), UDP `443` (`0.0.0.0/0` for HTTP/3 QUIC), ICMP Type `3` Code `4` (Path MTU Discovery).
  - **Egress:** All protocols to `0.0.0.0/0`.
- `oci_core_subnet.public` (`10.0.1.0/24`, regional public subnet)

#### Compute & Reserved Static IP (`main.tf`)
1. **Availability Domain:** Queries `data "oci_identity_availability_domains"` and picks `availability_domain_number` (default `1`, configurable `1..3` for multi-AD regions).
2. **Dynamic Canonical Ubuntu 24.04 Image Lookup:**
   - Queries `data "oci_core_images"` filtered by `operating_system = "Canonical Ubuntu"`, `operating_system_version = "24.04"`, and **`shape = var.instance_shape`** (automatically selecting `aarch64` for `A1.Flex`/`A2.Flex` and `x86_64` for `E2.1.Micro`/`E5.Flex`).
   - Includes `lifecycle { ignore_changes = [source_details[0].source_id, metadata["user_data"]] }` so future Oracle image updates never trigger unintended VM replacement.
3. **Flexible & Fixed Shape Support:**
   - Conditional `dynamic "shape_config"` block (`endswith(var.instance_shape, ".Flex")`) passes `ocpus` and `memory_in_gbs` for Flex shapes while remaining compatible with fixed shapes.
4. **Persistent Reserved Public IPv4 (`$0.00/mo`):**
   - Disables ephemeral public IP on the primary VNIC (`assign_public_ip = false`) and attaches a dedicated `oci_core_public_ip` (`lifetime = "RESERVED"`) to the primary private IP.

#### Input Variables: Always Free Defaults vs. Paid Tier (`oci/variables.tf`)

| Variable | Default (Always Free Safe) | Always Free Allowance | Paid / Scaled-Up Example |
|---|---|---|---|
| `oci_region` | *(required, e.g. `"us-sanjose-1"`)* | Home region | Any OCI region |
| `tenancy_ocid` | *(required)* | Tenancy OCID | Tenancy OCID |
| `compartment_ocid` | `null` *(uses `tenancy_ocid`)* | Root or child compartment | Dedicated compartment OCID |
| `instance_shape` | `"VM.Standard.A1.Flex"` (ARM64) | `VM.Standard.A1.Flex` or `VM.Standard.E2.1.Micro` | `"VM.Standard.A1.Flex"`, `"VM.Standard.A2.Flex"`, `"VM.Standard.E5.Flex"` |
| `instance_ocpus` | `2` | Up to `4` OCPUs across tenancy | `4`, `8`, etc. |
| `instance_memory_gb` | `12` | Up to `24` GB RAM across tenancy | `16`, `32`, etc. |
| `boot_volume_size_gb` | `50` | Up to `200` GB total block/boot storage | `100`+ GB |
| `swap_size_gb` | `2` | `0–8` GB | `0` or `2` |
| `admin_user` | `"ubuntu"` | `"ubuntu"` (Canonical Ubuntu default) | `"ubuntu"` |
| `ssh_allowed_cidrs` | `["0.0.0.0/0"]` | Restrict to `<your-ip>/32` | Restrict to `<your-ip>/32` |

> **OCI Account Tip (Documented in `oci/terraform.tfvars.example` & `deployment-guide.md`):**
> Upgrading an OCI Free Tier account to **Pay-As-You-Go (PAYG)** keeps the exact same **$0.00/mo Always Free allowance** (up to 4 A1 OCPUs, 24 GB RAM, 200 GB storage, 10 TB/mo egress), stops Oracle's 7-day idle-VM reclamation, and unlocks Paid host capacity pools so ARM `Out of host capacity` errors disappear. Set an OCI **Budget Alert at $1/month** as a safety guardrail.

#### OCI Ubuntu Cloud-Init Bootstrap (`oci/templates/user-data.sh.tftpl`)
- Dynamically detects OS (`ubuntu` / `noble` via `/etc/os-release`) and CPU architecture (`arm64` / `amd64`) for the official Docker APT repository.
- **Opens OCI Host `iptables` Ports:** OCI's Canonical Ubuntu image ships with `/etc/iptables/rules.v4` containing an `INPUT ... -j REJECT` rule blocking everything past port 22. Before starting Docker, `user-data.sh.tftpl` inserts `ACCEPT` rules for TCP `80`, TCP `443`, and UDP `443` before `REJECT` and runs `netfilter-persistent save` so Caddy's HTTP-01 TLS verification and HTTP/3 work immediately and persist across reboots.

---

## 4. Three-Stage Execution Plan

### Stage 1: Split Existing AWS Setup into `aws-lightsail/`, `dns-route53/`, and `backup-s3/` (Zero AWS Changes)

**Goal:** Refactor the directory layout and split your local `terraform.tfstate` so all three stacks report **`No changes. Your infrastructure matches the configuration.`** while the live server, Route 53 subdomain, and S3 backup bucket remain 100% untouched.

1. **Back up local state & configuration first (safety snapshot):**
   ```bash
   cp deploy/terraform/terraform.tfstate deploy/terraform/terraform.tfstate.pre-split.bak
   cp deploy/terraform/terraform.tfvars  deploy/terraform/terraform.tfvars.pre-split.bak
   ```
2. **Create the three AWS stack directories:**
   - `deploy/terraform/aws-lightsail/` (move `main.tf`, compute-only `variables.tf`/`outputs.tf`/`terraform.tfvars.example`, and verbatim `templates/user-data.sh.tftpl`).
   - `deploy/terraform/dns-route53/` (extract from `dns.tf` with `var.target_ip` replacing `aws_lightsail_static_ip.marginalia.ip_address` and removing the `count` wrapper on `aws_route53_record.marginalia`).
   - `deploy/terraform/backup-s3/` (extract from `s3.tf`, removing the `enable_s3_backup_bucket` `count` wrapper on core bucket resources while keeping `count` on lifecycle and optional IAM user).
3. **Split local `terraform.tfvars` into the three folders:**
   - `aws-lightsail/terraform.tfvars` (compute, SSH, tags)
   - `dns-route53/terraform.tfvars` (`aws_region`, `target_ip = "<LIGHTSAIL_STATIC_IP>"`, `subdomain_name`, `route53_zone_id`/`route53_zone_name`, `route53_ttl`)
   - `backup-s3/terraform.tfvars` (`aws_region`, `s3_bucket_name`/`s3_bucket_prefix`, `s3_backup_retention_days`, `create_s3_backup_iam_user`, `tags`)
4. **Initialize & migrate local state offline (`terraform state mv`):**
   Currently tracked in `deploy/terraform/terraform.tfstate`:
   - Compute (5 resources): `aws_lightsail_instance.marginalia`, `aws_lightsail_instance_public_ports.marginalia`, `aws_lightsail_key_pair.marginalia`, `aws_lightsail_static_ip.marginalia`, `aws_lightsail_static_ip_attachment.marginalia`
   - DNS (1 resource + 1 data source): `aws_route53_record.marginalia[0]`
   - S3 (4 resources): `aws_s3_bucket.backup[0]`, `aws_s3_bucket_lifecycle_configuration.backup[0]`, `aws_s3_bucket_public_access_block.backup[0]`, `aws_s3_bucket_server_side_encryption_configuration.backup[0]`

   Run:
   ```bash
   # 1. Initialize all three stacks
   terraform -chdir=deploy/terraform/aws-lightsail init
   terraform -chdir=deploy/terraform/dns-route53 init
   terraform -chdir=deploy/terraform/backup-s3 init

   # 2. Move Route 53 record into dns-route53/terraform.tfstate
   terraform state mv \
     -state=deploy/terraform/terraform.tfstate \
     -state-out=deploy/terraform/dns-route53/terraform.tfstate \
     'aws_route53_record.marginalia[0]' 'aws_route53_record.marginalia'

   # 3. Move S3 backup resources into backup-s3/terraform.tfstate
   terraform state mv \
     -state=deploy/terraform/terraform.tfstate \
     -state-out=deploy/terraform/backup-s3/terraform.tfstate \
     'aws_s3_bucket.backup[0]' 'aws_s3_bucket.backup'

   terraform state mv \
     -state=deploy/terraform/terraform.tfstate \
     -state-out=deploy/terraform/backup-s3/terraform.tfstate \
     'aws_s3_bucket_public_access_block.backup[0]' 'aws_s3_bucket_public_access_block.backup'

   terraform state mv \
     -state=deploy/terraform/terraform.tfstate \
     -state-out=deploy/terraform/backup-s3/terraform.tfstate \
     'aws_s3_bucket_server_side_encryption_configuration.backup[0]' 'aws_s3_bucket_server_side_encryption_configuration.backup'

   terraform state mv \
     -state=deploy/terraform/terraform.tfstate \
     -state-out=deploy/terraform/backup-s3/terraform.tfstate \
     'aws_s3_bucket_lifecycle_configuration.backup[0]' 'aws_s3_bucket_lifecycle_configuration.backup[0]'

   # 4. Move remaining Lightsail state into aws-lightsail/terraform.tfstate
   mv deploy/terraform/terraform.tfstate deploy/terraform/aws-lightsail/terraform.tfstate
   ```
5. **Verify Zero Infrastructure Changes Across All Three Stacks:**
   ```bash
   terraform -chdir=deploy/terraform/aws-lightsail plan   # Must report: No changes.
   terraform -chdir=deploy/terraform/dns-route53 plan     # Must report: No changes.
   terraform -chdir=deploy/terraform/backup-s3 plan       # Must report: No changes.
   ```
6. Clean up old root `.terraform/` and temporary `.bak` files once all three `plan` checks pass, then commit Stage 1.

---

### Stage 2: Add `deploy/terraform/oci/` & Update Documentation (Zero Production Impact)

**Goal:** Add the standalone OCI compute stack and update repository docs while production continues running undisturbed on AWS Lightsail.

1. Create `deploy/terraform/oci/`:
   - `main.tf`, `network.tf`, `variables.tf`, `outputs.tf`, `terraform.tfvars.example`, `templates/user-data.sh.tftpl`
2. Add `*.pem` to root `.gitignore`.
3. Validate formatting & configuration:
   ```bash
   terraform fmt -check -recursive deploy/terraform
   terraform -chdir=deploy/terraform/oci init -backend=false
   terraform -chdir=deploy/terraform/oci validate
   ```
4. Documentation updates:
   - Create `deploy/terraform/README.md` (stack matrix showing how users mix & match `aws-lightsail` or `oci` with optional `dns-route53` and `backup-s3`).
   - Update `docs/deployment-guide.md` (updated directory paths + OCI prerequisites, API key setup, PAYG + $1 budget alert tip, `ubuntu` vs `admin` SSH user).
   - Commit Stage 2.

---

### Stage 3: Switch Compute Instance Only (`aws-lightsail` &rarr; `oci`)

**Goal:** Stand up the OCI ARM instance, migrate the database, repoint `dns-route53/` to the new OCI IP, and retire the `$7/mo` Lightsail VM—while `backup-s3/` remains completely untouched.

#### Step 3.1: Pre-Lower DNS TTL (15–30 min before cutover)
1. In `deploy/terraform/dns-route53/terraform.tfvars`, temporarily set `route53_ttl = 60` and run:
   ```bash
   terraform -chdir=deploy/terraform/dns-route53 apply
   ```

#### Step 3.2: Provision OCI Compute Stack
1. Create OCI API signing key (`~/.oci/oci_api_key.pem`) and configure `deploy/terraform/oci/terraform.tfvars`.
2. Provision the OCI VCN, ARM instance (`VM.Standard.A1.Flex`), and Reserved Public IP:
   ```bash
   terraform -chdir=deploy/terraform/oci init
   terraform -chdir=deploy/terraform/oci apply
   ```
3. Note the `static_ip` output (`<OCI_STATIC_IP>`), SSH in (`ssh ubuntu@<OCI_STATIC_IP>`), and verify `/var/lib/marginalia-bootstrap.done` exists (`docker compose version`).

#### Step 3.3: Stage Application & Migrate Database
1. On OCI (`ubuntu@<OCI_STATIC_IP>`), clone the repository to `/opt/marginalia` and copy `deploy/.env` from Lightsail (`admin@<LIGHTSAIL_STATIC_IP>`):
   - **Zero `.env` edits required:** `DOMAIN`, `MYSQL_*`, Google/GitHub OAuth2 client credentials/callbacks, and `S3_BACKUP_*` variables stay identical!
2. Build containers and start MySQL on OCI while Lightsail is still live:
   ```bash
   cd /opt/marginalia/deploy
   docker compose build
   docker compose up -d mysql
   ```
3. Pause writes on Lightsail, take a final backup, and restore on OCI:
   ```bash
   # On Lightsail (admin@<LIGHTSAIL_STATIC_IP>):
   cd /opt/marginalia/deploy
   docker compose stop backend
   ./backup.sh
   ```
   Copy the newest `/opt/marginalia/backups/marginalia_*.sql.gz` to `/opt/marginalia/backups/` on OCI, then run on OCI:
   ```bash
   # On OCI (ubuntu@<OCI_STATIC_IP>):
   cd /opt/marginalia/deploy
   ./restore.sh /opt/marginalia/backups/marginalia_<timestamp>.sql.gz
   docker compose up -d
   ```

#### Step 3.4: Switch DNS Pointer in `dns-route53/`
1. Update **only** `target_ip` in `deploy/terraform/dns-route53/terraform.tfvars` from `"<LIGHTSAIL_STATIC_IP>"` to `"<OCI_STATIC_IP>"` and apply:
   ```bash
   terraform -chdir=deploy/terraform/dns-route53 apply
   ```
   *(Only 1 resource—`aws_route53_record.marginalia`—is updated in-place.)*
2. Within ~60 seconds, Caddy on OCI automatically acquires a Let's Encrypt TLS certificate for `<DOMAIN>`.

#### Step 3.5: Verify & Retire AWS Lightsail
1. Verify HTTPS access, Google/GitHub OAuth2 login, feed sync, and enable `marginalia-backup.timer` + AWS CLI S3 sync on OCI (`docs/deployment-guide.md` Section 9).
2. Restore `route53_ttl = 300` in `deploy/terraform/dns-route53/terraform.tfvars` and `apply`.
3. Keep Lightsail idle for 24–48 hours as an instant 1-line DNS rollback (`target_ip = "<LIGHTSAIL_STATIC_IP>"`).
4. Once confirmed stable, destroy the Lightsail stack:
   ```bash
   terraform -chdir=deploy/terraform/aws-lightsail destroy
   ```
   Because `dns-route53/` and `backup-s3/` live in separate directories and state files, `destroy` only removes the 5 `aws_lightsail_*` resources—eliminating the **$7.00/mo** charge with zero risk to DNS or S3 backups.
