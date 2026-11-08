# Changelog

All notable changes to **Marginalia** are documented in this file.

## [0.3.0] - 2026-11-08

- `e3f6fd1` docs: record OCI migration in dev-log and changelog, sanitize IP references (#31) (#34)
- `30779eb` fix(deploy): remove conflicting ip_address query parameter in oci_core_private_ips (#31) (#33)
- `4ad84f0` feat(deploy): modularize Terraform stacks and add Oracle Cloud (OCI) support (#31) (#32)
- `a908a95` feat(crawler): fallback to Open Graph (og:image / twitter:image) thumbnails (#29) (#30)
- `949ceed` feat(ui): add Next Feed / Next Folder button and compact More button (#27) (#28)
- `bdd421c` feat(ui): mobile UX improvements for stream footer, bottom nav, and Feeds drawer (#25) (#26)
- `0bbbc77` fix(ui): prevent iOS Safari viewport auto-zoom on filter modal and mobile inputs (#23) (#24)
- `d3cd535` feat: add per-feed title filter words/phrases to exclude matching articles (#16) (#22)
- `40e3b83` fix(crawler): auto-detect channel title and normalize nested HTML in RSS items (#1) (#21)
- `66a005f` feat(ui): add ability to delete folders in sidebar (#20)

## [0.2.0] - 2026-11-03

- `5180ff3` style(frontend): update color theme and brand icons to pale blue (#14)
- `0a170e8` feat: support drag-and-drop reordering for folders and feeds (#12)
- `2fbb711` feat(ui): improve stream card layout, font setting, and mobile reading view (#11)
- `3b134dc` fix(ui): make mobile long-press work across all scrolled articles (#10)
- `1bfb081` fix(ui): prevent iOS Safari from dismissing mobile long-press menu (#9)
- `daceba9` feat(ui): add "Make above as read" button and mobile long-press menu (#8)
- `0c8a871` fix(ui): prevent iOS Safari double-tap on sidebar feed items (#6)
- `f701f2b` ci: add GitHub Actions workflow for backend tests and frontend build (#4)

## [0.1.0] - 2026-11-02

- Initial release of Marginalia (Spring Boot 4.1 + Java 25 backend, React 19 PWA frontend, OPML import/export, OAuth2 + email whitelist authentication, and AWS Lightsail Docker Compose + Caddy deployment).
