# Email Whitelist Administration Guide

This guide explains how to manage user access in **Marginalia**, the safety measures protecting the system against lockouts, and instructions for adding and removing authorized email addresses.

---

## 1. Safety Measures in Place

To ensure system reliability, security, and administrative continuity, Marginalia enforces multiple safety layers:

### 1. Dual-Layer Anti-Lockout Defense (Bootstrap Administrator)
* The environment variable `AUTH_WHITELIST_EMAILS` serves as a permanent **bootstrap administrator list**.
* **Startup Seeding**: On application startup, `AuthWhitelistService` automatically checks if the bootstrap emails exist in the database table (`whitelist_emails`). If not, it inserts them.
* **Fallback Gate**: Even if the database table is accidentally truncated, emptied, or corrupted, any email defined in `AUTH_WHITELIST_EMAILS` is unconditionally authorized to log in. You can **never** be locked out of your instance.

### 2. Anti-Self-Deletion Guard
* An administrator cannot delete their own email address from the whitelist via the API or UI.
* Attempting to remove yourself returns an HTTP `400 Bad Request` with:
  `"Cannot remove your own email from the whitelist"`

### 3. Bootstrap Protection Rule
* Any email address configured in the `AUTH_WHITELIST_EMAILS` environment variable cannot be removed through the API by anyone.
* Attempting to delete a bootstrap admin returns an HTTP `400 Bad Request` with:
  `"Cannot remove bootstrap administrator email via API. Remove it from AUTH_WHITELIST_EMAILS in environment configuration instead."`

### 4. Role-Based Access Restriction
* The whitelist management endpoints (`/api/admin/whitelist/**`) are protected by `AdminWhitelistController`.
* Only authenticated users whose email is listed among the bootstrap administrators are permitted to view, add, or delete whitelist records. All other users receive HTTP `403 Forbidden`.

### 5. Email Normalization
* All email addresses are automatically trimmed of leading/trailing whitespace and converted to lowercase before comparison and storage (`User@Example.COM` -> `user@example.com`).

---

## 2. Managing Whitelist via REST API

The administrative endpoints are accessible at `/api/admin/whitelist`. All requests require an active authenticated administrator session cookie (`MARGINALIA_SESSION`).

### 1. List All Whitelisted Emails
```bash
curl -X GET "https://your-domain.com/api/admin/whitelist" \
  -H "Cookie: MARGINALIA_SESSION=<session-id>"
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "email": "owner@example.com",
    "note": "Bootstrap Administrator",
    "addedBy": "SYSTEM",
    "createdAt": "2026-09-14T20:00:00"
  },
  {
    "id": 2,
    "email": "colleague@example.com",
    "note": "Research Partner",
    "addedBy": "owner@example.com",
    "createdAt": "2026-09-14T21:15:00"
  }
]
```

---

### 2. Add an Email Address
```bash
curl -X POST "https://your-domain.com/api/admin/whitelist" \
  -H "Content-Type: application/json" \
  -H "Cookie: MARGINALIA_SESSION=<session-id>" \
  -d '{
    "email": "friend@example.com",
    "note": "Friend invite"
  }'
```

**Response (201 Created):**
```json
{
  "id": 3,
  "email": "friend@example.com",
  "note": "Friend invite",
  "addedBy": "owner@example.com",
  "createdAt": "2026-09-14T22:30:00"
}
```

*The user can now immediately sign in using Google or GitHub. Their user account will be created automatically on first login.*

---

### 3. Remove an Email Address

#### By ID:
```bash
curl -X DELETE "https://your-domain.com/api/admin/whitelist/3" \
  -H "Cookie: MARGINALIA_SESSION=<session-id>"
```
**Response:** `204 No Content`

#### By Email Address:
```bash
curl -X DELETE "https://your-domain.com/api/admin/whitelist?email=friend@example.com" \
  -H "Cookie: MARGINALIA_SESSION=<session-id>"
```
**Response:** `204 No Content`

---

## 3. Direct Database Administration (Emergency / CLI)

If you have direct SSH access to the host server and want to view or modify the whitelist directly in MySQL:

### Connect to the MySQL Container:
```bash
docker exec -it marginalia-mysql-dev mysql -u marginalia_user -p marginalia
```

### View Whitelist:
```sql
SELECT id, email, note, added_by, created_at FROM whitelist_emails;
```

### Manually Authorize an Email:
```sql
INSERT INTO whitelist_emails (email, note, added_by) 
VALUES ('friend@example.com', 'Added via SQL', 'admin');
```

### Manually Revoke an Email:
```sql
DELETE FROM whitelist_emails WHERE email = 'friend@example.com';
```

### Invalidate Active Sessions for a Revoked User:
When an email is removed, their active browser session will expire on its own or fail upon re-authentication. To terminate their session immediately:
```sql
DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = 'friend@example.com';
```

---

## 4. Summary of Access Control Rules

| Action | Permitted For | Guard Condition |
|---|---|---|
| View Whitelist | Bootstrap Administrators | Must be authenticated as admin email |
| Add Email | Bootstrap Administrators | Valid email format; duplicate check |
| Remove Regular Email | Bootstrap Administrators | Allowed |
| Remove Self | None | Blocked (anti-self-deletion) |
| Remove Bootstrap Admin | None | Blocked (must modify `AUTH_WHITELIST_EMAILS` env var) |
| Log In | Whitelisted Email (DB or Env) | Must match Google or GitHub authenticated email |
| Unauthorized Login | Anyone else | Blocked (`403 Forbidden` / `error=unauthorized`) |
