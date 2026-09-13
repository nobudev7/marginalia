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
