# Marginalia — Production Deployment Guide & Lightsail Runbook

This guide provides an end-to-end operational runbook for provisioning, configuring, and launching **Marginalia** in production on an AWS Lightsail instance using Terraform, Docker Compose, and Caddy with automated HTTPS.

---

## Architecture Overview

```
[ Internet Traffic / Clients ]
       │
       │ Ports 80, 443 (TCP/UDP HTTP/3)
       ▼
┌────────────────────────────────────────────────────────┐
│ AWS Lightsail Instance (Debian 12)                     │
│ Static Public IPv4: <static_ip>                        │
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

---

## Prerequisites

Before beginning, ensure your local workstation has:

1. **Terraform CLI** (v1.5.0 or later): `terraform -version`
2. **AWS CLI** (v2): `aws --version`, configured with credentials having Lightsail (and optionally Route 53 and S3) permissions:
   ```bash
   aws configure
   ```
3. **SSH Key Pair**: A local public key (default: `~/.ssh/id_ed25519.pub`). If you need to generate one:
   ```bash
   ssh-keygen -t ed25519 -C "admin@marginalia"
   ```
4. **Domain Name**: A registered domain or subdomain (e.g., `reader.example.com` or `marginalia.example.com`).

---

## Phase 1: Local Infrastructure Provisioning (Terraform)

All declarative infrastructure resides in the `deploy/terraform/` directory.

### 1. Initialize Terraform
Navigate to the Terraform directory and initialize the AWS provider:

```bash
cd deploy/terraform
terraform init
```

### 2. Configure Deployment Variables
Copy the template to create your personal variables file:

```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your settings:

```hcl
# AWS Region & Compute Size (Note: Lightsail is in us-west-2, us-east-1, us-east-2; not us-west-1)
aws_region    = "us-east-1"

instance_name = "marginalia"
bundle_id     = "micro_3_0"   # 1 GB RAM, 2 vCPUs (~$7/mo with IPv4)
swap_size_gb  = 2             # 2 GB swap ensures memory stability

# SSH Security
public_key_path   = "~/.ssh/id_ed25519.pub"
ssh_allowed_cidrs = ["0.0.0.0/0"] # Or restrict to your IP: ["203.0.113.10/32"]

# Route 53 DNS Automation (Optional)
# Set to true if your root domain hosted zone is in Route 53:
manage_route53_record = false
# subdomain_name        = "reader.yourdomain.com"
# route53_zone_name     = "yourdomain.com"

# AWS S3 Off-Site Backups (Optional)
# Set to true to create a dedicated encrypted backup bucket:
enable_s3_backup_bucket   = false
# s3_backup_retention_days = 90
# create_s3_backup_iam_user = false
```

### 3. Review Plan & Apply
Preview changes to ensure all resources match expectations:

```bash
terraform plan
```

Provision the infrastructure:

```bash
terraform apply
```

Upon completion, Terraform prints the assigned static IP and SSH connection string:

```text
Apply complete! Resources: 4 added, 0 changed, 0 destroyed.

Outputs:
dns_a_record_target = "198.51.100.42"
instance_name = "marginalia"
ssh_command = "ssh -i ~/.ssh/id_ed25519 admin@198.51.100.42"
static_ip = "198.51.100.42"
```

---

## Phase 2: DNS & Domain Routing

Your domain or subdomain must route to the Lightsail static IP so Caddy can automatically obtain a Let's Encrypt TLS certificate.

### Option A: Automated via Route 53 (Terraform)
If you enabled `manage_route53_record = true` in Phase 1, Terraform automatically creates the `A` record pointing to the static IP.

Verify resolution locally:
```bash
dig +short reader.yourdomain.com
# Output should match your static_ip
```

### Option B: Manual DNS Configuration
If managing DNS manually in Route 53, Cloudflare, Namecheap, or another registrar:

1. Open your DNS provider's dashboard.
2. Add a new **A Record**:
   * **Host / Name**: `reader` (or your chosen subdomain prefix)
   * **Type**: `A`
   * **Value / Points to**: The `static_ip` output from Phase 1 (e.g., `198.51.100.42`)
   * **TTL**: `300` seconds (5 minutes)
3. Wait 1–2 minutes for propagation, then verify:
   ```bash
   nslookup reader.yourdomain.com
   ```

> [!IMPORTANT]
> DNS must resolve to your Lightsail static IP **before** starting the Docker containers. Caddy completes an automated ACME HTTP-01 challenge upon initial launch; if DNS does not point to the host, certificate issuance will fail.

---

## Phase 3: Host Verification & Application Setup

### 1. SSH into the Lightsail Instance
Use the command from Terraform outputs:

```bash
ssh -i ~/.ssh/id_ed25519 admin@<static_ip>
```

### 2. Verify First-Boot Cloud-Init Bootstrap
Lightsail executes `templates/user-data.sh.tftpl` on initial boot. Verify completion:

```bash
# Wait for cloud-init to finish (if just launched)
cloud-init status --wait

# Inspect bootstrap log
sudo cat /var/log/cloud-init-output.log
```

Confirm that the core prerequisites are active:

```bash
# 1. Verify swap is active
sudo swapon --show
free -h

# 2. Verify Docker Engine and compose plugin
docker --version
docker compose version

# 3. Verify fail2ban is running
sudo systemctl status fail2ban --no-pager
```

### 3. Deploy Application Code to Host
Clone the repository into `/opt/marginalia` (the directory created by bootstrap with ownership assigned to `admin`):

```bash
cd /opt
git clone https://github.com/your-username/marginalia.git
cd /opt/marginalia
```

*(Alternatively, copy your repository directory using `rsync` or `scp` if using a private repository without host git credentials).*

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

# Optional S3 Off-Site Backups
# S3_BUCKET=s3://your-marginalia-backup-bucket/marginalia
```

> [!TIP]
> You can generate strong random passwords on Linux with:
> ```bash
> openssl rand -base64 24
> ```

---

## Phase 5: Container Launch & TLS Verification

### 1. Build and Launch Containers
Start the production container stack in detached mode:

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

# Resource safeguards for 1 GB RAM VPS
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
Trigger a manual execution to verify everything operates smoothly:

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
To monitor individual service logs:

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

# Restore specific archive
./deploy/restore.sh deploy/backups/marginalia_marginalia_20261007_020000.sql.gz
```

The restore script automatically checks archive integrity, confirms target database schema, validates Flyway migrations, and audits row counts.

### 4. Infrastructure Decommissioning
If you ever need to completely tear down the cloud infrastructure:

```bash
cd deploy/terraform
terraform destroy
```

---

## Troubleshooting Guide

| Issue | Root Cause | Solution |
|---|---|---|
| Caddy fails to obtain TLS certificate | DNS A-record not pointing to static IP or ports 80/443 blocked | Verify DNS with `dig +short $DOMAIN_NAME`. Check Lightsail firewall rules in `main.tf` to ensure port 80 and 443 are open. |
| Backend terminates unexpectedly | Out of memory (OOM) | Verify swap file is active (`swapon --show`). Ensure `JAVA_OPTS` contains `-XX:MaxRAMPercentage=75.0`. |
| Database connection refused | MySQL container still initializing | Inspect MySQL logs (`docker compose logs mysql`). The backend automatically waits for MySQL healthchecks before connecting. |
| OAuth2 callback returns 400 or Redirect URI mismatch | Authorized Redirect URI misconfigured in Google/GitHub console | Verify registered callback URL matches `https://<DOMAIN_NAME>/login/oauth2/code/<provider>` exactly. |
| Backup script reports access denied | Incorrect database credentials | Verify `DB_USER` and `DB_PASSWORD` in `deploy/.env` match those initialized in `mysql_data`. |
