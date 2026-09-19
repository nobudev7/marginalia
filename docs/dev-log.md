# Marginalia — Development Log & Verification Diary

This document serves as the project diary, recording dated verification results, local testing notes, commands, and architectural decisions throughout development. Entries are ordered chronologically (oldest at the top, newest at the bottom).

---

## 2026-09-13 — Phase 1: Initial Scaffolding & Database Verification

### Scope & Goals
* Scaffold the repository foundation with updated `.gitignore` and `README.md`.
* Initialize backend with Java 25 LTS and Spring Boot 4.1.1.
* Create initial database schema with Flyway (`V1__initial_schema.sql`).
* Implement JPA entities and Spring Data repositories for `users`, `categories`, `feeds`, `articles`, and `article_user_state`.
* Set up and verify local MySQL 8.4 container via Docker Compose.

### Verification Checklist
- [x] Java 25 & Spring Boot 4 compilation: `./mvnw clean test-compile` (`BUILD SUCCESS` in 1.1s)
- [x] Automated test suite: `./mvnw test` verifying Flyway schema application and repository CRUD (3.7s)
- [x] Docker container initialization: `docker compose -f compose.dev.yaml up -d`
- [x] Database connectivity: verified `marginalia_user` login with `utf8mb4` encoding and `utf8mb4_unicode_ci` collation
- [x] Live Flyway migration: applied `V1__initial_schema.sql` to local MySQL 8.4 container
- [x] Schema inspection: verified tables, foreign keys, compound indexes, and unique constraints in MySQL

### Useful Commands

```bash
# Start local MySQL container
docker compose -f compose.dev.yaml up -d

# Stop local MySQL container
docker compose -f compose.dev.yaml down

# Reset local MySQL container and volume
docker compose -f compose.dev.yaml down -v

# Connect to interactive MySQL CLI
docker exec -it marginalia-mysql-dev mysql -u marginalia_user -pmarginalia_pass marginalia

# One-liner table inspection
docker exec marginalia-mysql-dev mysql -u marginalia_user -pmarginalia_pass marginalia -e "SHOW TABLES;"
docker exec marginalia-mysql-dev mysql -u marginalia_user -pmarginalia_pass marginalia -e "DESCRIBE feeds; DESCRIBE articles;"
docker exec marginalia-mysql-dev mysql -u marginalia_user -pmarginalia_pass marginalia -e "SELECT * FROM flyway_schema_history;"

# Run Flyway migrations directly against local MySQL
./mvnw flyway:migrate '-Dflyway.url=jdbc:mysql://localhost:3306/marginalia?useSSL=false&allowPublicKeyRetrieval=true' \
  -Dflyway.user=marginalia_user \
  -Dflyway.password=marginalia_pass

# Run test suite
./mvnw test
```

### Technical Notes & Decisions
* **MySQL 8.4 LTS Compatibility**: Removed `--default-authentication-plugin=caching_sha2_password` from `compose.dev.yaml` as MySQL 8.4 made `caching_sha2_password` the mandatory default and removed the legacy configuration flag.
* **Spring Boot 4 / Flyway Module**: Used `spring-boot-starter-flyway` which bundles Spring Boot 4's new `spring-boot-flyway` autoconfiguration module alongside `flyway-mysql`.
* **Standardized GUID Key**: Set article `guid` to `VARCHAR(255)` so composite unique constraint `(feed_id, guid)` works cleanly without requiring length prefixes.
* **Java 25 Virtual Threads**: Enabled `spring.threads.virtual.enabled: true` in `application.yml` for future feed crawler tasks.
 
---

## 2026-09-13 — Phase 2: Feed Ingestion Engine & OPML

### Scope & Goals
* Implement `FeedCrawlerService` using ROME (`com.rometools:rome:2.1.0`) to parse RSS 2.0 and Atom 1.0 feeds.
* Implement conditional HTTP caching (`If-None-Match` / ETag and `If-Modified-Since` / Last-Modified) using Spring's `RestClient` to prevent redundant bandwidth.
* Implement article deduplication by composite key `(feed_id, guid)`.
* Implement `OpmlService` using standard JDK XML APIs for importing feeds from standard OPML files and exporting OPML 2.0 documents.
* Set up `@Scheduled` background task for periodic feed crawling configured via `app.crawler.interval`.
* Implement REST controllers: `CategoryController`, `FeedController`, `ArticleController`, `OpmlController`, and `GlobalExceptionHandler`.
* Write automated tests for ROME parsing, conditional HTTP 304, deduplication, OPML import/export, and API endpoints.

### Verification Checklist
- [x] Compilation on Java 25: `./mvnw test-compile` (`BUILD SUCCESS` in 1.1s)
- [x] Full automated test suite: `./mvnw test` (28 tests passed, 0 failures, 0 errors in 5.3s)
  - `FeedCrawlerServiceTest`: RSS 2.0 parsing, Atom 1.0 parsing, HTTP 304 Not Modified caching, article deduplication by guid, error handling.
  - `OpmlServiceTest`: OPML import with categories, duplicate feed prevention, OPML 2.0 export.
  - `ApiControllerTest`: Category CRUD, Feed CRUD + refresh, paginated article stream, read/saved state toggling, batch mark-as-read, unread count, OPML import/export endpoints, error handling.
  - `MarginaliaApplicationTests`: Application context boot and full Flyway + JPA CRUD pipeline.

### REST API Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/categories` | List categories for current user |
| `POST` | `/api/categories` | Create a new category (`name`, `sortOrder`) |
| `PUT` | `/api/categories/{id}` | Update category |
| `DELETE` | `/api/categories/{id}` | Delete category |
| `GET` | `/api/feeds` | List all subscribed feeds |
| `GET` | `/api/feeds/{id}` | Get feed details |
| `POST` | `/api/feeds` | Subscribe to a new feed (`feedUrl`, `title`, `categoryId`) |
| `PUT` | `/api/feeds/{id}` | Update feed metadata or category |
| `DELETE` | `/api/feeds/{id}` | Unsubscribe from feed |
| `POST` | `/api/feeds/{id}/refresh` | Manually trigger feed crawl |
| `GET` | `/api/articles` | Query paginated articles (`feedId`, `categoryId`, `saved`, `page`, `size`) |
| `GET` | `/api/articles/{id}` | Get single article with read/saved state |
| `PATCH` | `/api/articles/{id}` | Update read/saved state (`read`, `saved`) |
| `PUT` | `/api/articles/{id}/read` | Mark article as read/unread (`?read=true`) |
| `PUT` | `/api/articles/{id}/save` | Toggle saved bookmark (`?saved=true`) |
| `POST` | `/api/articles/mark-all-read` | Bulk mark articles as read (`[id1, id2, ...]`) |
| `GET` | `/api/articles/unread-count` | Get total unread articles count |
| `POST` | `/api/opml/import` | Import feeds from OPML file (multipart `file`) |
| `GET` | `/api/opml/export` | Export subscriptions as OPML 2.0 XML |
| `GET` | `/api/auth/me` | Current authenticated user profile (401 if unauthenticated) |
| `GET` | `/api/auth/status` | Non-throwing authentication status check (`authenticated: boolean`) |
| `GET` | `/api/auth/providers` | Available OAuth2 provider initiation URLs (`google`, `github`) |
| `POST` | `/api/auth/logout` | Invalidate session and clear session cookie (204 No Content) |
| `GET`/`POST` | `/api/auth/dev-login` | Local dev authentication helper (`?email=...`, dev mode only) |
| `GET` | `/api/admin/whitelist` | List all whitelisted emails (admin only) |
| `POST` | `/api/admin/whitelist` | Add email to whitelist (`email`, `note`) (admin only) |
| `DELETE` | `/api/admin/whitelist/{id}` | Remove email from whitelist by ID (admin only) |
| `DELETE` | `/api/admin/whitelist?email=...` | Remove email from whitelist by address (admin only) |

### Technical Notes & Decisions
* **Spring Boot 4 `RestClient` Configuration**: Built using `RestClient.builder()` with `JdkClientHttpRequestFactory`, 15s connect timeout, 30s read timeout, and automatic redirect following.
* **Conditional HTTP 304 Handling**: Used `RestClient.exchange()` to inspect response status codes directly; for HTTP 304 (Not Modified), empty body parsing is avoided and feed metadata is updated without touching the article store.
* **Spring Data JPA N+1 Optimization**: Added `@EntityGraph(attributePaths = {"category"})` to `FeedRepository` queries and `@EntityGraph(attributePaths = {"feed"})` to `ArticleRepository` queries. Batch-loaded `ArticleUserState` rows using `findByUserIdAndArticleIdIn` during article stream enrichment.
* **XML Processing Security**: Disabled external DTDs (`disallow-doctype-decl`) in `DocumentBuilderFactory` to prevent XXE vulnerabilities during OPML import.
* **Jackson 3 in Spring Boot 4**: Spring Boot 4.1 uses `tools.jackson.core:jackson-databind:3.1.5` instead of Jackson 2 packages.
* **Entity Fetch Strategy (FetchType.LAZY)**: Maintained `@ManyToOne(fetch = FetchType.LAZY)` across all entity relationships to avoid global Cartesian query bloat and cascading N+1 queries. Query-specific parent fetching is handled via `@EntityGraph(attributePaths = {"category"})` on repository methods. Added `NonTransactionalImportTest` to guarantee DTO mapping without ambient sessions.
* **Single Responsibility Principle & Service Decoupling**: Fully separated `FeedService` (feed data CRUD operations) from `FeedCrawlerService` (remote HTTP crawling, XML parsing, article ingestion). `FeedService.addFeed` strictly persists the feed record and does not invoke the crawler. Client applications or background tasks trigger crawls independently (via `POST /api/feeds/{id}/refresh` or scheduled jobs), preventing cross-service coupling and eliminating network I/O from feed creation transactions.
* **Fixed Unread Count for Sparse User State**: `getUnreadCount()` previously queried `article_user_state` rows where `is_read = false`, which returned 0 for newly crawled articles (no state row exists until a user interacts). Replaced with a JPQL `NOT EXISTS` subquery on `ArticleRepository` that counts all articles belonging to the user's feeds where no `is_read = true` state row exists. Articles with no state row are now correctly treated as unread.
* **Batched `markAllAsRead()` to Eliminate N+1 Queries**: Previously looped N times calling individual `SELECT` + `INSERT/UPDATE` per article (2N database round-trips). Now batch-fetches all existing `ArticleUserState` rows in one `findByUserIdAndArticleIdIn` query, updates them in memory, creates new state objects for missing article IDs using `getReferenceById` (no SELECT), and persists everything in a single `saveAll` call. Reduces database round-trips from 2N to 2.
* **Spring Session JDBC Persistence**: Added `spring-session-jdbc` and `V2__spring_session.sql` Flyway migration. Configured `@EnableJdbcHttpSession` with 90-day (`7,776,000s`) session lifetime, custom cookie name `MARGINALIA_SESSION`, `SameSite=Lax`, and `HttpOnly`. Sessions survive application and container restarts in MySQL.
* **Multi-Provider OAuth2 & Whitelist Security**: Supported Google and GitHub OAuth2 sign-in. Enforced strict email whitelist verification (`app.auth.whitelist-emails`) via `AuthWhitelistService` in `CustomOAuth2UserService` and `CustomOidcUserService`. Handles private GitHub emails via `/user/emails` API fallback. Configured `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` so unauthenticated API requests return 401 rather than HTML redirect loops.
* **Database-Backed Email Whitelist**: Added `V3__whitelist.sql` migration creating `whitelist_emails`. Whitelist entries are maintained in MySQL and managed via `AdminWhitelistController` (`/api/admin/whitelist`). Enforced anti-lockout safety measures: automatic seeding from `AUTH_WHITELIST_EMAILS` on startup, dual-source fallback (bootstrap admins are always authorized even if the database table is cleared), anti-self-deletion guard (admins cannot delete their own email), and bootstrap protection (bootstrap admins cannot be deleted via the API).
* **Saved Articles Sort Remapping**: Resolved `PropertyReferenceException: No property 'publishedAt' found for type 'ArticleUserState'`. The controller endpoint `@PageableDefault` defaults sort to `publishedAt DESC` (appropriate for general `Article` queries). When `saved = true`, `ArticleService.getSavedArticles` queries `ArticleUserState` where `publishedAt` does not exist directly. Implemented `remapSortForSavedArticles` to map any `publishedAt` sort order to `savedAt DESC` so bookmarked articles are sorted chronologically by when they were saved.

---

## 2026-09-18 — Phase 4 (Step 4.1): Frontend Scaffolding, Design Tokens & Development Proxy

### Scope & Goals
* Scaffold Vite React (TypeScript) project in `frontend/`.
* Configure Tailwind CSS with `@tailwindcss/typography`, PostCSS, Autoprefixer, Lucide icons, `clsx`, and `tailwind-merge`.
* Establish warm editorial palette (`paper`, `ink`, `amberAccent`) and editorial typography (`Newsreader` serif, `Inter` sans).
* Configure Vite development reverse-proxy routing `/api`, `/oauth2`, and `/logout` requests to the Spring Boot backend on port 8080.
* Define TypeScript API models in `src/types/index.ts` mirroring backend DTOs.
* Implement base HTTP client in `src/api/client.ts` supporting session cookies (`credentials: 'include'`).
* Create starter shell in `src/App.tsx` displaying design tokens, typography, component previews, and live reverse-proxy verification against the Spring Boot backend.

### Verification Checklist
- [x] Frontend compilation & bundle: `npm run build` (`dist/` generated with 0 errors in 471ms)
- [x] Vite dev server startup: `npm run dev` running on `http://127.0.0.1:5173`
- [x] Development proxy validation: `curl -s http://127.0.0.1:5173/api/auth/status` forwards to Spring Boot on `:8080` returning `{"authenticated":false}`
- [x] Frontend shell serving: `curl -s http://127.0.0.1:5173/` returns `<title>Marginalia</title>` and loads editorial font links
- [x] Backend test suite regression check: `./mvnw test` (55 tests passed, 0 failures, 0 errors)

### Useful Frontend Commands

```bash
# Start frontend development server with HMR
cd frontend && npm run dev

# Run TypeScript typecheck and production build
cd frontend && npm run build

# Preview production build locally
cd frontend && npm run preview

# Verify development proxy against running Spring Boot backend
curl -s http://localhost:5173/api/auth/status
```

### Technical Notes & Decisions
* **TypeScript Strict Mode Compatibility**: Declared class properties explicitly in `ApiError` (`src/api/client.ts`) rather than parameter properties (`public status: number`) to comply with TypeScript's `erasableSyntaxOnly` compiler option. Used `import type` for interfaces to comply with `verbatimModuleSyntax`.
* **Session Cookie Preservation across Proxy**: Configured `credentials: 'include'` on all `apiFetch` requests so the `MARGINALIA_SESSION` cookie is preserved across calls made through Vite's development proxy without CORS configuration needed on the backend.
* **Warm Editorial Design Tokens**: Defined `#FAF8F5` (body background), `#1C1917` (warm charcoal text), `#E8E1D9` (borders), and `#B45309` (warm amber accents) in `tailwind.config.js` to avoid cold neutral grays and provide a print-inspired aesthetic suitable for extended reading sessions.
* **Backend DTO Type Parity**: Mirrored backend DTO records (`ArticleResponse`, `FeedResponse`, `CategoryResponse`, `AuthResponse`, `WhitelistResponse`) into TypeScript interfaces in `src/types/index.ts`, including Spring Data's `PageResponse<T>` wrapper for paginated endpoints.

---

## 2026-09-18 — Phase 4 (Step 4.2): Authentication & Session Gate

### Scope & Goals
* Implement `AuthContext` and `useAuth` hook managing authentication state, loading indicators, and error reporting.
* Build minimalist `LoginPage` with editorial styling, branded Google and GitHub OAuth2 sign-in buttons, and whitelist access advisory.
* Support unauthorized redirect feedback (`?error=unauthorized`) with dismissible alert banner.
* Provide local development login shortcut (`/api/auth/dev-login`) for testing without live OAuth provider credentials.
* Implement application `Header` displaying user display name, email, avatar, and logout trigger (`/api/auth/logout`).
* Build authenticated `AppLayout` displaying active session credentials and persistence diagnostics.

### Verification Checklist
- [x] Compilation & bundle: `npm run build` (`dist/` generated with 0 errors in 768ms)
- [x] Unauthenticated gate: Visiting `http://localhost:5173` without a session renders `LoginPage`
- [x] Unauthorized query banner: Visiting `http://localhost:5173/?error=unauthorized` displays the amber whitelist access warning banner and cleans URL query string
- [x] Local dev-login execution: Submitting `test@example.com` triggers `/api/auth/dev-login`, establishes MySQL-backed session cookie, and transitions instantly to `AppLayout`
- [x] Session persistence: Reloading the browser preserves the session without returning to the login gate
- [x] Logout flow: Clicking "Log Out" invokes `POST /api/auth/logout`, clears session cookie, and returns to `LoginPage`
- [x] Regression testing: All 55 backend tests continue to pass (`./mvnw test`)

### Technical Notes & Decisions
* **Seamless Session Bootstrapping**: `AuthContext` calls `/api/auth/status` on mount (a non-throwing endpoint) to quietly discover existing sessions without generating 401 console error noise in the browser developer tools.
* **URL Sanitization on Error Display**: When handling `?error=unauthorized`, `AuthContext` captures the message into state and uses `window.history.replaceState({}, document.title, window.location.pathname)` to strip the query string from the browser address bar, ensuring the alert does not persist upon subsequent manual page refreshes.
* **Persistent Cookie Flow via Proxy**: The `MARGINALIA_SESSION` cookie is managed with `SameSite=Lax` and `HttpOnly`. Because the Vite dev server proxies `/api`, browser security treats all requests as same-origin, allowing cookies to attach automatically.
* **Dev-Login Whitelist Enforcement**: Updated `AuthController.devLogin` to validate the submitted email against `AuthWhitelistService.isWhitelisted` instead of auto-whitelisting arbitrary addresses. Unwhitelisted addresses now return `403 Forbidden` and trigger an access denied alert banner on the frontend login card.

---

## 2026-09-19 — Phase 4 (Step 4.3): Navigation, Collapsible Sidebar & Feed Subscription

### Scope & Goals
* Implement `useCategories` and `useFeeds` hooks managing feed and category data, subscription creation, deletion, and unread counts.
* Implement grouped unread count query in `ArticleRepository` and endpoint `GET /api/articles/unread-counts` for live per-feed badges.
* Build collapsible `Sidebar` featuring primary stream filters ("All Articles", "Unread Only", "Saved") and category folder accordions with live unread badge counters.
* Build `AddFeedModal` dialog with RSS/Atom URL input, title override, and inline category assignment or creation.
* Support responsive mobile drawer with hamburger toggle button in `Header` and backdrop touch dismissal.
* Connect two-pane navigation flow in `AppLayout`.

### Verification Checklist
- [x] Frontend compilation & bundle: `npm run build` (`dist/` generated with 0 errors in 490ms)
- [x] Backend test suite: `./mvnw test` (57 tests passed, 0 failures, 0 errors)
- [x] Sidebar navigation: "All Articles", "Unread Only", "Saved" views highlight when active and display unread counts
- [x] Category accordions: Smooth expand/collapse animation for folder groups with unread badges
- [x] Feed subscription modal: Entering an RSS feed URL creates feed, triggers initial crawl, and refreshes sidebar feeds
- [x] Mobile drawer: Resizing viewport <768px collapses sidebar into header hamburger menu; tapping slides out full drawer over backdrop blur

### Technical Notes & Decisions
* **Spring MVC Path Variable Disambiguation**: Added regex constraint `/{id:\\d+}` on numeric endpoints and placed literal endpoints (`/unread-counts`, `/unread-count`) before path variable routes in `ArticleController` to eliminate type mismatch routing conflicts.
* **Grouped Unread SQL Optimization**: Implemented a single `SELECT a.feed.id, COUNT(a) ... GROUP BY a.feed.id` query on `ArticleRepository` to fetch all feed unread counts in a single database query, avoiding N+1 count queries across subscribed feeds.
* **Defensive Frontend Unread Polling**: `useFeeds` queries `/api/articles/unread-counts` and gracefully falls back to `/api/articles/unread-count` if running against an un-restarted backend.
* **Responsive Drawer Architecture**: Implemented desktop sticky positioning (`md:flex sticky top-[53px]`) alongside mobile fixed backdrop overlay (`fixed inset-0 md:hidden`) to provide native-feeling drawer navigation on phone screens.



