# Marginalia — Production Deployment Guide (AWS Lightsail & Oracle Cloud)

This guide provides an end-to-end operational runbook for provisioning, configuring, and launching **Marginalia** in production on either **AWS Lightsail** (~$7/mo) or **Oracle Cloud Infrastructure (OCI)** (`$0/mo` Always Free ARM or Paid shapes) using modular Terraform stacks, Docker Compose, and Caddy with automated HTTPS.

---

## Architecture Overview

```
[ Internet Traffic / Clients ]
       │
       │ Ports 80, 443 (TCP/UDP HTTP/3)
       ▼
┌────────────────────────────────────────────────────────┐
│ Compute Host:                                          │
│   • AWS Lightsail (Debian 12, x86_64, user: admin) OR  │
│   • Oracle Cloud OCI (Ubuntu 24.04, ARM64/x86, ubuntu) │
│ Persistent Static Public IPv4: <static_ip>             │
│ 2 GB Swap File (/swapfile, swappiness=10)              │
│ Docker Engine + Compose Plugin                         │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Docker Bridge Network (marginalia_network)       │  │
│  │                                                  │  │
│  │  ┌──────────────┐   ┌──────────────┐             │  │
│  │  │ Caddy Proxy  │──▶│ Backend API  │             │  │
│  │  │ (Auto HTTPS) │   │ (Spring Boot)│             │  │
│  │  └──────────────┘   └──────┬───────┘             │  │
│  │                            │ (Port 3306)         │  │
│  │                            ▼                     │  │
│  │                     ┌──────────────┐             │  │
│  │                     │ MySQL 8.4 DB │             │  │
│  │                     └──────────────┘             │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  systemd.timer: Daily 02:00 UTC Database Backup        │
└───────────────────────┬────────────────────────────────┘
                        │ Optional Encrypted Sync
                        ▼
       [ AWS S3 Private Backup Bucket ]
```

### Modular Terraform Layout (`deploy/terraform/`)

Compute, DNS routing, and backup storage live in decoupled Terraform stacks with separate state files so you can mix providers freely and replace servers without touching DNS zones or historical backups:

* `deploy/terraform/aws-lightsail/` — AWS Lightsail compute, static IP, and firewall (~$7/mo)
* `deploy/terraform/oci/` — OCI VCN, Always Free ARM (`$0/mo`) or Paid compute, Reserved Public IP, and security list
* `deploy/terraform/dns-route53/` — *(Optional)* AWS Route 53 subdomain `A` record pointing to any server's `target_ip`
* `deploy/terraform/backup-s3/` — *(Optional)* Private encrypted AWS S3 bucket + retention lifecycle for daily backups

---

## Prerequisites

Ensure your local workstation has:

1. **Terraform CLI** (v1.5.0 or later): `terraform -version`
2. **SSH Key Pair**: A local public key (default: `~/.ssh/id_ed25519.pub`). Generate one if needed:
   ```bash
   ssh-keygen -t ed25519 -C "admin@marginalia"
   ```
3. **Domain Name**: A registered domain or subdomain (e.g., `reader.example.com` or `marginalia.example.com`).
4. **Cloud Provider Credentials** (for whichever stacks you use):
   * **For `aws-lightsail/`, `dns-route53/`, or `backup-s3/`**: AWS CLI v2 (`aws configure`) with credentials scoped to Lightsail, Route 53, and/or S3.
   * **For `oci/`**: An Oracle Cloud account and OCI API Signing Key:
     1. In the OCI Console, open **Profile (top-right) &rarr; User settings &rarr; API keys &rarr; Add API key**, download the private key to `~/.oci/oci_api_key.pem` (`chmod 600 ~/.oci/oci_api_key.pem`), and copy the displayed `~/.oci/config` snippet (Tenancy OCID, User OCID, Fingerprint, and Home Region).
     2. **Recommended OCI Account Tip**: Upgrading an OCI Free Tier account to **Pay-As-You-Go (PAYG)** retains the exact same **$0.00/mo Always Free allowance** (up to 4 Ampere A1 OCPUs, 24 GB RAM, 200 GB Block/Boot storage, 10 TB/mo egress), prevents Oracle's 7-day idle-VM reclamation, and unlocks Paid capacity pools so ARM `Out of host capacity` errors disappear. Set a **$1/month Budget Alert** under **Billing & Cost Management &rarr; Budgets** as a safety guardrail.

---

## Phase 1: Local Infrastructure Provisioning (Terraform)

Choose **Option A (Oracle Cloud)** or **Option B (AWS Lightsail)** for compute, plus optional off-site backup storage (`backup-s3/`).

### Option A: Provision Compute on Oracle Cloud (`deploy/terraform/oci/`)

Supports **both** OCI Always Free (`$0/mo` defaults) and Paid shapes via `terraform.tfvars`:

```bash
cd deploy/terraform/oci
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your OCI region, Tenancy OCID, authentication settings (`~/.oci/config` or explicit API key paths), and desired shape:

```hcl
oci_region   = "us-sanjose-1" # Use your tenancy's Home Region for Always Free
tenancy_ocid = "ocid1.tenancy.oc1..example"

# Always Free ARM64 defaults ($0/mo within 4 OCPU / 24 GB tenancy allowance):
instance_shape      = "VM.Standard.A1.Flex"
instance_ocpus      = 2
instance_memory_gb  = 12
boot_volume_size_gb = 50
swap_size_gb        = 2

public_key_path   = "~/.ssh/id_ed25519.pub"
ssh_allowed_cidrs = ["0.0.0.0/0"] # Recommended: restrict to ["<YOUR_IP>/32"]
```

Initialize, preview, and apply:

```bash
terraform init
terraform plan
terraform apply
```

---

### Option B: Provision Compute on AWS Lightsail (`deploy/terraform/aws-lightsail/`)

```bash
cd deploy/terraform/aws-lightsail
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:

```hcl
# Note: Lightsail is supported in us-west-2, us-east-1, us-east-2 (not us-west-1)
aws_region    = "us-east-1"
instance_name = "marginalia"
bundle_id     = "micro_3_0" # 1 GB RAM, 2 vCPUs (~$7/mo with static IPv4)
swap_size_gb  = 2           # 2 GB swap ensures memory stability

public_key_path   = "~/.ssh/id_ed25519.pub"
ssh_allowed_cidrs = ["0.0.0.0/0"] # Recommended: restrict to ["<YOUR_IP>/32"]
```

Initialize, preview, and apply:

```bash
terraform init
terraform plan
terraform apply
```

---

### Compute Outputs (Identical Across Both Providers)

Upon completion, Terraform outputs your persistent static IPv4 address and ready-to-run SSH command (`ubuntu@<static_ip>` on OCI, `admin@<static_ip>` on Lightsail):

```text
Outputs:
dns_a_record_target = "198.51.100.42"
instance_name       = "marginalia"
ssh_command         = "ssh -i ~/.ssh/id_ed25519 ubuntu@198.51.100.42"
static_ip           = "198.51.100.42"
```

---

### Optional: Provision S3 Off-Site Backup Storage (`deploy/terraform/backup-s3/`)

To store encrypted daily MySQL backups off-site in AWS S3 (works with both Lightsail and OCI hosts):

```bash
cd deploy/terraform/backup-s3
cp terraform.tfvars.example terraform.tfvars
# Optionally set create_s3_backup_iam_user = true for dedicated upload credentials
terraform init
terraform apply
```

Note the `s3_backup_bucket_uri` output (`s3://<bucket-name>/marginalia`) for Phase 4.

---

## Phase 2: DNS & Domain Routing

Your domain or subdomain must route to `static_ip` so Caddy can obtain a Let's Encrypt TLS certificate.

### Option A: Automated via Route 53 (`deploy/terraform/dns-route53/`)

If your domain's hosted zone is in AWS Route 53 (regardless of whether compute runs on Lightsail or OCI):

```bash
cd deploy/terraform/dns-route53
cp terraform.tfvars.example terraform.tfvars
```

Set `target_ip` to the `static_ip` output from Phase 1:

```hcl
aws_region        = "us-east-1"
target_ip         = "198.51.100.42"
subdomain_name    = "reader.yourdomain.com"
route53_zone_name = "yourdomain.com"
route53_ttl       = 300
```

Apply and verify DNS propagation:

```bash
terraform init
terraform apply
dig +short reader.yourdomain.com
```

### Option B: Manual DNS Configuration

If managing DNS manually in Cloudflare, Namecheap, Porkbun, Route 53 Console, or another registrar (skip `deploy/terraform/dns-route53/` completely):

1. Open your DNS provider's dashboard.
2. Add an **A Record**:
   * **Host / Name**: `reader` (or your chosen subdomain prefix)
   * **Type**: `A`
   * **Value / Points to**: The `static_ip` output from Phase 1 (e.g., `198.51.100.42`)
   * **TTL**: `300` seconds (5 minutes)
3. Wait 1–2 minutes for propagation, then verify:
   ```bash
   nslookup reader.yourdomain.com
   ```

> [!IMPORTANT]
> DNS must resolve to your server's `static_ip` **before** starting the Docker containers. Caddy completes an automated ACME HTTP-01 challenge upon initial launch; if DNS does not point to the host (or Cloudflare proxy orange-cloud intercepts HTTP-01 awkwardly), certificate issuance can fail.

---

## Phase 3: Host Verification & Application Setup

### 1. SSH into the Instance
Run the `ssh_command` printed by Terraform (`admin@<static_ip>` on Lightsail Debian 12, or `ubuntu@<static_ip>` on OCI Ubuntu 24.04):

```bash
# OCI:
ssh -i ~/.ssh/id_ed25519 ubuntu@<static_ip>

# AWS Lightsail:
ssh -i ~/.ssh/id_ed25519 admin@<static_ip>
```

### 2. Verify First-Boot Cloud-Init Bootstrap
Both providers run `templates/user-data.sh.tftpl` on initial boot. Verify completion:

```bash
# Wait for cloud-init to finish (if just launched)
cloud-init status --wait

# Inspect bootstrap log
sudo cat /var/log/cloud-init-output.log
```

Confirm that core prerequisites are active:

```bash
# 1. Verify swap is active
sudo swapon --show
free -h

# 2. Verify Docker Engine and Compose plugin
docker --version
docker compose version

# 3. Verify fail2ban is running
sudo systemctl status fail2ban --no-pager
```

*(Optional — if syncing backups to AWS S3 via `S3_BUCKET`, ensure AWS CLI v2 is installed and configured for `root`, e.g., via the official `awscli-exe-linux-$(uname -m).zip` installer and `sudo aws configure`.)*

### 3. Deploy Application Code to Host
Clone the repository into `/opt/marginalia` (pre-created by cloud-init and owned by your SSH user):

```bash
git clone https://github.com/your-username/marginalia.git /opt/marginalia
cd /opt/marginalia
```

---

## Phase 4: Production Environment Configuration

### 1. Create Production Environment File
Copy the documented template:

```bash
cd /opt/marginalia/deploy
cp .env.example .env
chmod 600 .env
```

### 2. Edit Production Variables
Edit `/opt/marginalia/deploy/.env`:

```bash
nano .env
```

Configure the following production parameters:

```bash
# Domain & TLS Configuration
DOMAIN_NAME=reader.yourdomain.com
ACME_EMAIL=  # Optional: leave blank or set a real email

# Database Credentials (Generate strong random passwords)
DB_NAME=marginalia
DB_USER=marginalia_user
DB_PASSWORD=YOUR_STRONG_DB_PASSWORD_HERE
MYSQL_ROOT_PASSWORD=YOUR_STRONG_ROOT_PASSWORD_HERE

# OAuth2 Authentication (Google and/or GitHub)
# Callback URL registered in provider: https://reader.yourdomain.com/login/oauth2/code/<provider>
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret

# Access Control Whitelist (Comma-separated emails authorized to log in)
AUTH_WHITELIST_EMAILS=your-email@example.com

# Production Security Flags
DEV_MODE=false
SECURE_COOKIE=true
SPRING_PROFILES_ACTIVE=prod

# Background Crawler Settings
CRAWLER_INTERVAL=15m
CRAWLER_MAX_FEED_SIZE=10MB

# Optional S3 Off-Site Backups (from deploy/terraform/backup-s3 output)
# S3_BUCKET=s3://your-marginalia-backup-bucket/marginalia
```

> [!TIP]
> Generate strong random passwords on Linux with:
> ```bash
> openssl rand -base64 24
> ```

---

## Phase 5: Container Launch & TLS Verification

All container images in `deploy/compose.yaml` publish multi-architecture (`linux/amd64` and `linux/arm64`) manifests, so builds run natively on both x86_64 Lightsail and ARM64 OCI Ampere instances.

### 1. Build and Launch Containers
```bash
cd /opt/marginalia/deploy
docker compose -f compose.yaml up -d --build
```

### 2. Verify Cascading Healthchecks
Docker Compose manages startup dependencies: MySQL initializes first &rarr; Backend runs Flyway migrations &rarr; Caddy begins reverse proxying.

Monitor container status until all services report `(healthy)`:

```bash
docker compose -f compose.yaml ps
```

Expected output:
```text
NAME                 IMAGE                       COMMAND                  SERVICE   CREATED          STATUS                    PORTS
marginalia-backend   deploy-backend              "java -jar app.jar"      backend   30 seconds ago   Up 30 seconds (healthy)   
marginalia-caddy     deploy-caddy                "caddy run --config …"   caddy     30 seconds ago   Up 30 seconds (healthy)   0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp, 0.0.0.0:443->443/udp
marginalia-mysql     mysql:8.4                   "docker-entrypoint.s…"   mysql     30 seconds ago   Up 30 seconds (healthy)   3306/tcp
```

### 3. Verify Let's Encrypt TLS Issuance
Inspect Caddy logs to confirm successful ACME certificate provisioning:

```bash
docker compose -f compose.yaml logs caddy | grep -i "certificate"
```

Look for:
```text
certificate obtained successfully {"identifier": "reader.yourdomain.com"}
```

### 4. Browser Verification
1. Navigate to `https://reader.yourdomain.com` in your web browser.
2. Verify the valid TLS padlock icon.
3. Test OAuth2 login using your whitelisted email address.
4. Add an RSS feed or import an OPML file to verify end-to-end functionality.

---

## Phase 6: Automated Database Backup Scheduling

Configure automated daily hot backups using `systemd.timer` (executes transiently at 02:00 UTC with zero idle memory overhead).

### 1. Create the systemd Service
Create `/etc/systemd/system/marginalia-backup.service`:

```bash
sudo nano /etc/systemd/system/marginalia-backup.service
```

Paste the following configuration:

```ini
[Unit]
Description=Marginalia Automated Database Backup
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
User=root
WorkingDirectory=/opt/marginalia
ExecStart=/opt/marginalia/deploy/backup.sh
StandardOutput=journal
StandardError=journal

# Resource safeguards for small VPS instances
Nice=19
IOSchedulingClass=best-effort
IOSchedulingPriority=7
```

### 2. Create the systemd Timer
Create `/etc/systemd/system/marginalia-backup.timer`:

```bash
sudo nano /etc/systemd/system/marginalia-backup.timer
```

Paste the following configuration:

```ini
[Unit]
Description=Run Marginalia Database Backup Daily at 02:00 UTC

[Timer]
OnCalendar=*-*-* 02:00:00
Persistent=true

[Install]
WantedBy=timers.target
```

### 3. Enable and Start the Timer
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now marginalia-backup.timer
```

### 4. Verify Immediate Test Run
```bash
# Execute the backup unit
sudo systemctl start marginalia-backup.service

# Check execution logs
journalctl -u marginalia-backup.service -n 30 --no-pager

# Verify created backup archive
ls -lh /opt/marginalia/deploy/backups/
```

Confirm that the backup archive has `-rw-------` (`600`) permissions and non-zero size.

---

## Phase 7: Day-2 Operations & Maintenance

### 1. Updating Application Code
When deploying updates from git:

```bash
cd /opt/marginalia
git pull

# Rebuild and recreate updated containers with zero downtime
docker compose -f deploy/compose.yaml up -d --build
```

### 2. Viewing Live Logs
```bash
cd /opt/marginalia/deploy

# Follow all service logs
docker compose logs -f

# Follow backend only
docker compose logs -f backend

# Follow Caddy access logs
docker compose logs -f caddy
```

### 3. Disaster Recovery (Restoring from Backup)
To restore a backup into production:

```bash
cd /opt/marginalia
./deploy/restore.sh deploy/backups/marginalia_marginalia_20261007_020000.sql.gz
```

The restore script automatically checks archive integrity, confirms target database schema, validates Flyway migrations, and audits row counts.

### 4. Migrating Compute Providers or Decommissioning
Because compute, DNS (`dns-route53/`), and S3 backups (`backup-s3/`) are separated into independent Terraform stacks:

* **Switching servers (e.g., Lightsail &rarr; OCI):** Provision the new compute stack, restore the latest backup archive on the new server, update `target_ip` in `deploy/terraform/dns-route53/terraform.tfvars`, and run `terraform apply`.
* **Decommissioning old compute:** Run `terraform destroy` inside `deploy/terraform/aws-lightsail/` or `deploy/terraform/oci/`. Your `dns-route53/` record and `backup-s3/` bucket remain completely untouched unless you explicitly destroy those stacks.

---

## Troubleshooting Guide

| Issue | Root Cause | Solution |
|---|---|---|
| Caddy fails to obtain TLS certificate | DNS A-record not pointing to `static_ip` or ports 80/443 blocked | Verify DNS with `dig +short $DOMAIN_NAME`. On Lightsail, check `aws_lightsail_instance_public_ports`. On OCI, verify both `oci_core_security_list` in `network.tf` and host `iptables` (`sudo iptables -L INPUT -n --line-numbers`). |
| OCI `terraform apply` reports `Out of host capacity` | Free Tier ARM pool temporarily full in selected Availability Domain | Try `availability_domain_number = 2` (or `3`) in `oci/terraform.tfvars`, or upgrade the OCI account to Pay-As-You-Go (still `$0/mo` within Always Free limits, with a `$1` Budget Alert). |
| Backend terminates unexpectedly | Out of memory (OOM) | Verify swap file is active (`swapon --show`). Ensure `JAVA_OPTS` contains `-XX:MaxRAMPercentage=75.0`. |
| Database connection refused | MySQL container still initializing | Inspect MySQL logs (`docker compose logs mysql`). The backend automatically waits for MySQL healthchecks before connecting. |
| OAuth2 callback returns 400 or Redirect URI mismatch | Authorized Redirect URI misconfigured in Google/GitHub console | Verify registered callback URL matches `https://<DOMAIN_NAME>/login/oauth2/code/<provider>` exactly. |
| Backup script reports access denied | Incorrect database credentials | Verify `DB_USER` and `DB_PASSWORD` in `deploy/.env` match those initialized in `mysql_data`. |
