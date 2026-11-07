# Marginalia — Modular Terraform Deployment Stacks

Marginalia separates **compute**, **DNS routing**, and **off-site backup storage** into independent Terraform directories with isolated state files. You can mix and match providers freely—and replace or migrate compute instances without risking DNS records or database backups.

---

## Stack Matrix

| Directory | Role | Required? | Provider | Typical Monthly Cost |
|---|---|---|---|---|
| [`aws-lightsail/`](aws-lightsail/) | Compute + Static IP + Firewall (Option 1) | Pick **one** compute stack | `hashicorp/aws` | ~$7/mo (`micro_3_0`) |
| [`oci/`](oci/) | VCN + Compute + Reserved IP + Firewall (Option 2) | Pick **one** compute stack | `oracle/oci` | **$0/mo** (Always Free `A1.Flex`) or Paid |
| [`dns-route53/`](dns-route53/) | Subdomain `A` record &rarr; `target_ip` | Optional *(skip if managing DNS manually)* | `hashicorp/aws` | ~$0.50/mo per Route 53 hosted zone |
| [`backup-s3/`](backup-s3/) | Private encrypted S3 backup bucket + lifecycle | Optional *(skip for local-only backups)* | `hashicorp/aws` | ~$0.02/mo |

---

## Quickstart

### 1. Provision Compute (Choose Either `oci` or `aws-lightsail`)

#### Option A: Oracle Cloud Infrastructure (`$0/mo` Always Free or Paid)
```bash
cd deploy/terraform/oci
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your oci_region, tenancy_ocid, API key / ~/.oci/config, and SSH key
terraform init
terraform apply
```

#### Option B: AWS Lightsail (~$7/mo)
```bash
cd deploy/terraform/aws-lightsail
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your aws_region, bundle_id, and SSH key
terraform init
terraform apply
```

Both stacks export the same core outputs (`static_ip`, `ssh_command`, `dns_a_record_target`).

---

### 2. Point Your Domain (`dns-route53/` or Manual Registrar)

- **Manual DNS (Cloudflare, Namecheap, Porkbun, etc.):** Create an `A` record pointing your subdomain to the `static_ip` output from Step 1. Done—skip `dns-route53/`.
- **Automated AWS Route 53 (`dns-route53/`):**
  ```bash
  cd deploy/terraform/dns-route53
  cp terraform.tfvars.example terraform.tfvars
  # Set subdomain_name, route53_zone_name (or route53_zone_id), and target_ip
  terraform init
  terraform apply
  ```
  *(Tip: When migrating between `aws-lightsail` and `oci`, update `target_ip` in `dns-route53/terraform.tfvars` and run `terraform apply`.)*

---

### 3. Provision Off-Site Backup Bucket (Optional — `backup-s3/`)

Works identically whether your server runs on AWS Lightsail or OCI (giving OCI deployments cross-cloud disaster recovery):

```bash
cd deploy/terraform/backup-s3
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform apply
```

Copy the `s3_backup_bucket_uri` output into `S3_BUCKET` inside `/opt/marginalia/deploy/.env` on your server.

---

For end-to-end host setup, OAuth2 configuration, TLS verification, and automated daily backup scheduling, see the **[Production Deployment Guide](../../docs/deployment-guide.md)**.
