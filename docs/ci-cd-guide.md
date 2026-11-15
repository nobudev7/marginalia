# Marginalia — CI/CD & Automated Deployment Guide

This guide documents Marginalia's branch-based CI/CD pipeline using **GitHub Actions**, **Branch Rulesets**, and cloud-agnostic automated SSH deployment to the production host (**Oracle Cloud Infrastructure (OCI)**, **AWS Lightsail**, or any Linux VPS running Docker).

---

## Pipeline Overview

```text
1. Feature Branch ──▶ git push origin <branch>
                            │
                            ▼
2. Open Pull Request ──▶ [.github/workflows/ci.yml]
   (targeting main)      Runs Backend (Java 25 / Maven) + Frontend (Node 22 / Vite)
                            │
                            ▼
3. Merge Gate         ──▶ GitHub Ruleset blocks merge until both CI checks pass
                            │
                            ▼
4. Merge to main     ──▶ [.github/workflows/deploy.yml]
                         SSHs to Server (/opt/marginalia) ──▶ git pull ──▶ docker compose up -d --build
```

---

## 1. Branch Protection Ruleset (`main`)

All changes must flow through a feature branch and Pull Request before reaching `main`:

1. Navigate to **Settings → Rules → Rulesets** in the GitHub repository and create a branch ruleset targeting `main`.
2. Enable the following protections:
   - **Restrict deletions** & **Block force pushes**
   - **Require a pull request before merging** (disallows direct `git push` to `main`)
   - **Require status checks to pass** → Add both CI job names from [`.github/workflows/ci.yml`](../.github/workflows/ci.yml):
     - `Backend (Java 25 & Maven)`
     - `Frontend (Node 22 & Vite)`
3. *(Optional)* Enable **Allow auto-merge** under **Settings → General → Pull Requests** so PRs can be queued to merge automatically as soon as tests pass.

---

## 2. Continuous Integration ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml))

The CI workflow runs automatically whenever a Pull Request targeting `main` is opened or updated (and on pushes to `main`):

- **Backend (`Backend (Java 25 & Maven)`)**: Runs `./mvnw -B test` on Temurin JDK 25 with Maven dependency caching.
- **Frontend (`Frontend (Node 22 & Vite)`)**: Runs `npm ci`, `npm run lint` (`oxlint`), and `npm run build` (`tsc -b && vite build`) on Node 22.

---

## 3. Server SSH Key & GitHub Secrets Setup

Automated deployment uses a dedicated Ed25519 keypair stored in GitHub Actions Secrets so personal SSH keys remain local.

### Generate and Authorize the Deploy Key

```bash
# 1. Generate a dedicated passwordless Ed25519 deploy keypair locally
ssh-keygen -t ed25519 -C "github-actions-marginalia-deploy" -f ~/.ssh/marginalia_deploy -N ""

# 2. Authorize the public key on your production host
#    (Use "ubuntu" on OCI Ubuntu 24.04 or "admin" on AWS Lightsail Debian 12)
ssh <DEPLOY_USER>@<SERVER_STATIC_IP> "cat >> ~/.ssh/authorized_keys" < ~/.ssh/marginalia_deploy.pub

# 3. Capture the server's hashed SSH host key for known_hosts verification
ssh-keyscan -H <SERVER_STATIC_IP> 2>/dev/null
```

### Configure GitHub Repository Secrets

Under **Settings → Secrets and variables → Actions → New repository secret**, create **4 separate secrets**:

| Secret Name | Value |
|---|---|
| `DEPLOY_HOST` | The server's static IPv4 address (e.g., `203.0.113.10`) |
| `DEPLOY_USER` | SSH username (`ubuntu` on OCI Ubuntu 24.04; `admin` on AWS Lightsail Debian 12) |
| `DEPLOY_SSH_KEY` | Full multi-line contents of `~/.ssh/marginalia_deploy` |
| `DEPLOY_KNOWN_HOSTS` | Output from `ssh-keyscan -H <SERVER_STATIC_IP> 2>/dev/null` |

> [!NOTE]
> GitHub-hosted `ubuntu-latest` runners connect from rotating public IPs. Port `22` must allow runner ingress (`ssh_allowed_cidrs = ["0.0.0.0/0"]` in Terraform, protected by `fail2ban` and key-only authentication). If you later restrict port `22` to a single IP, switch to a Tailscale GitHub Action or a self-hosted runner on the server.

---

## 4. Automated Deployment ([`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml))

When changes merge into `main`, `deploy.yml` connects to the production server and performs a rolling build and restart:

1. **Path Filtering**: Automatically skips deployment for documentation-only merges (`docs/`, `README.md`, `CHANGELOG.md`). Triggers only when files under `backend/**`, `frontend/**`, `deploy/compose.yaml`, `deploy/Caddyfile`, `deploy/mysql.cnf`, or `.github/workflows/deploy.yml` change—or when triggered manually via **Actions → Deploy to Production → Run workflow** (`workflow_dispatch`).
2. **Concurrency Guard**: Uses `group: production-deploy` with `cancel-in-progress: false` so concurrent merges queue sequentially rather than interrupting an active deployment.
3. **Selective Rebuilds via Docker Layer Cache**: Running `docker compose -f compose.yaml up -d --build` relies on the multi-stage Dockerfiles (`backend/Dockerfile` and `frontend/Dockerfile`), which separate dependency installation (`pom.xml`, `package*.json`) from `src/`. Unchanged services hit 100% build cache in under a second and are **not** recreated, while `marginalia-mysql` continues running uninterrupted.
4. **Disk Hygiene**: Executes `docker image prune -f` after rollout to prevent dangling build layers from filling the boot volume.

---

## 5. Switching Cloud Providers (OCI ↔ AWS Lightsail)

Because `deploy.yml` is cloud-agnostic and both Terraform stacks provision `/opt/marginalia` identically:
1. Authorize `~/.ssh/marginalia_deploy.pub` in `~/.ssh/authorized_keys` on the new host.
2. Update `DEPLOY_HOST`, `DEPLOY_USER` (`ubuntu` vs. `admin`), and `DEPLOY_KNOWN_HOSTS` in GitHub Secrets—no workflow YAML edits required.

> [!TIP]
> On small 1 GB RAM instances (such as AWS Lightsail `micro_3_0`), building Maven/Vite containers directly on the server alongside running containers can trigger heavy swap usage; consider a 2 GB+ instance (`small_3_0`) or pre-building images in GitHub Actions if running on AWS.

---

## 6. Troubleshooting

| Issue | Likely Cause | Resolution |
|---|---|---|
| `git pull --ff-only` fails on server | Local manual edits or untracked files in `/opt/marginalia` | SSH to the server, inspect `git status` in `/opt/marginalia`, and clean or commit stray changes. |
| SSH timeout during deploy | Port 22 blocked by cloud firewall (OCI Security List / Lightsail Public Ports) or `fail2ban` | Check `ssh_allowed_cidrs` in your Terraform config and verify `/etc/fail2ban` status on host. |
| Host key verification failed | Server was rebuilt or switched cloud providers | Re-run `ssh-keyscan -H <SERVER_STATIC_IP> 2>/dev/null` and update the `DEPLOY_KNOWN_HOSTS` secret. |
| Deployment skipped after merge | Merge only touched `docs/` or Terraform files | Expected behavior from the `paths:` filter. Use **Actions → Deploy to Production → Run workflow** to force a deploy manually. |
