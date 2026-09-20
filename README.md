# Marginalia

A private, self-hosted RSS reader web application designed for seamless, distraction-free reading across desktop and mobile devices.

## Goal & Direction

Marginalia is designed to provide a clean, personal reading experience:

* **Distraction-Free Reading**: A focused reading space with article streams, categorized collections, unread count tracking, and bookmarking.
* **Desktop & Mobile First**: A responsive web application and Progressive Web App (PWA) installable directly to mobile home screens.
* **Architecture**:
  * **Backend**: Java 25 & Spring Boot 4 for reliable feed ingestion, OPML processing, and scheduling.
  * **Frontend**: React (Vite) + Tailwind CSS for a fast, responsive UI.
  * **Database**: MySQL for structured relational storage of feeds, articles, and reading states.
* **Private & Self-Hosted**: Single-user design with authenticated access, long-lived sessions, and containerized deployment via Docker Compose.

---

## Project Plan & Architecture Summary

### Tech Stack
* **Frontend**: React (TypeScript, Vite), Tailwind CSS, Lucide Icons, `vite-plugin-pwa`.
* **Backend**: Java 25 LTS, Spring Boot 4.1.x, Spring Data JPA, Spring Security (OAuth2), ROME Tools (RSS/Atom parsing).
* **Database**: MySQL 8.x with Flyway database migrations.
* **Deployment**: AWS Lightsail Linux instance running Docker Compose + Caddy (automated Let's Encrypt HTTPS).

### Key Features
1. **Reader Interface**: Collapsible sidebar with categories, live unread badges, stream list/card view, and reading drawer.
2. **Feed Ingestion**: Periodic background crawler using Java 25 Virtual Threads and conditional HTTP (`ETag` / `If-Modified-Since`) to minimize bandwidth.
3. **OPML Support**: Bulk import and export of feeds and category folders.
4. **Security & Session**: OAuth2 single-user login restricted to an email whitelist, with persistent 60–90 day sessions.
5. **PWA & Mobile**: Offline-capable service worker, app manifest, and full-screen mobile app shell.

### Implementation Roadmap
* [x] **Phase 1: Project Scaffolding & Database Setup** — Backend foundation, Flyway schema, JPA entities/repositories, local Docker compose.
* [x] **Phase 2: Feed Ingestion Engine & OPML** — ROME crawler, conditional HTTP fetching, OPML import/export, and background scheduler.
* [x] **Phase 3: Security & Session Persistence** — OAuth2 client (Google & GitHub), database-backed email whitelist, persistent 90-day JDBC sessions.
* [ ] **Phase 4: Frontend Development (React PWA)** — Responsive stream layout, reading pane, unread counters, and PWA manifest.
* [ ] **Phase 5: Deployment & AWS Lightsail Automation** — Multi-stage Dockerfiles, Caddyfile, and automated S3 backup script.

---

## Documentation

* [OAuth2 Setup Guide](docs/oauth2-setup-guide.md) — Step-by-step Google and GitHub OAuth2 configuration.
* [Local Development Guide](docs/local-development-guide.md) — Activating the `dev` profile, safe defaults, and testing dev-login.
* [Email Whitelist Administration Guide](docs/whitelist-admin-guide.md) — Managing access, safety measures, and admin REST endpoints.
* [Future Expansion to Public Service (Path B)](docs/future-expansion-public-service.md) — Technical migration plan for global feed deduplication and multi-user scaling.
* [Development Log](docs/dev-log.md) — Detailed changelog, architectural decisions, and API reference.



