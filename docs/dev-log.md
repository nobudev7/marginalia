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

