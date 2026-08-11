# IAST Quality Management System / Quality Website

Internal web portal and Document Management System (DMS) for IAST Software Solutions covering automotive software quality management, ASPICE (Automotive SPICE) process compliance, quality gates, process reference models (PRM), issue management workflows, master lists, lessons learned, and downloadable templates for engineering teams.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend Framework** | Spring Boot 3.5.0, Java 21 |
| **Security** | Spring Security 6 (BCrypt, Form Auth, Remember-Me, HTTPS enforcement) |
| **Database** | MySQL 8.0+ (Spring Data JPA, Hibernate, LONGBLOB DMS storage) |
| **Build & Tooling** | Maven 3.x (`mvnw` / `mvnw.cmd` wrapper included) |
| **Frontend & View** | HTML5, Vanilla CSS, Vanilla JavaScript, Thymeleaf |
| **Email & Inspection** | Spring Mail (SMTP), Apache Tika 2.9.2 (MIME type verification) |

---

## Architecture & DMS Storage

The IAST Quality Portal features a centralized, authoritative database-backed Document Management System (DMS):

- **Database-Authoritative Access**: Every publicly downloadable document requires an `APPROVED` `DocumentMaster` record and an `APPROVED` `DocumentVersion` record in MySQL.
- **LONGBLOB File Storage**: Binary file content is stored directly within MySQL `LONGBLOB` (`file_data`), ensuring strict transactional integrity and eliminating reliance on un-indexed local filesystem storage.
- **Zero Unregistered Filesystem Fallback**: Static requests (`/documents/**`, `/uploaded-documents/**`) are intercepted by the DMS controller. Files not registered or unapproved return **HTTP 404 Not Found**.
- **Version History**: Full document revision tracking is preserved ($1.0 \rightarrow 1.1 \rightarrow 2.0$).

---

## Security Controls

The application incorporates multi-layered security controls:

- **Spring Security Authentication & Authorization**: Form-based authentication (`/admin/login`); administrative APIs (`/api/admin/**`) and views (`/admin/**`) require `ROLE_ADMIN`.
- **Password Protection & Validation**: Passwords are hashed using BCrypt. Account creation and password updates enforce password policy (length, uppercase, lowercase, numbers, special characters, weak password check).
- **Production Remember-Me Secret Key**: Production profile (`SPRING_PROFILES_ACTIVE=prod`) validates that `REMEMBER_ME_KEY` is present and does not use default development strings, failing fast on startup if unconfigured.
- **HTTPS Enforcement**: When `SSL_ENABLED=true`, `http.requiresChannel(c -> c.anyRequest().requiresSecure())` mandates HTTPS for all incoming requests.
- **HTTP → HTTPS Redirection**: Dual-port listener automatically redirects plain HTTP requests to HTTPS.
- **HSTS (HTTP Strict Transport Security)**: Enforces `maxAge=31536000` (1 year) with `includeSubDomains`.
- **Production CORS Filtering**: In production, `CorsConfig` automatically strips wildcard (`*`), `localhost`, and `127.0.0.1` origins, permitting only explicitly configured origins.
- **Path Traversal Protection**: Rejects all file requests containing `..`, null bytes (`\0`), or invalid path characters.
- **SMTP Password Masking**: Settings API masks `smtp_password` as `"********"` in GET responses and prevents overwriting existing database secrets.

---

## Build & Test Commands

### 1. Execute Automated Test Suite

Runs all unit, integration, and security test cases:

```bash
# Linux / macOS
./mvnw clean test

# Windows
.\mvnw.cmd clean test
```

### 2. Build Production Package

Compiles and packages the runnable production JAR archive:

```bash
# Linux / macOS
./mvnw clean package -DskipTests

# Windows
.\mvnw.cmd clean package -DskipTests
```

The resulting executable artifact is created at:
`target/quality-backend-0.0.1-SNAPSHOT.jar`

---

## Production Configuration

Production configuration is supplied via environment variables. Copy `.env.example` to `.env` (or set variables directly in your hosting platform). Never commit secrets into source control.

### Environment Variables

| Variable | Description | Example Placeholder |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |
| `PORT` | Production HTTPS server port | `443` |
| `http.port` | Production HTTP redirect port | `80` |
| `SSL_ENABLED` | Enable HTTPS channel enforcement | `true` |
| `DB_URL` | MySQL JDBC connection URL | `jdbc:mysql://<DB_HOST>:3306/quality_website?useSSL=true&requireSSL=true` |
| `DB_USERNAME` | MySQL database user | `<PRODUCTION_DB_USER>` |
| `DB_PASSWORD` | MySQL database password | `<PRODUCTION_DB_PASSWORD>` |
| `DDL_AUTO` | JPA DDL execution strategy (MUST be `validate` in prod) | `validate` |
| `REMEMBER_ME_KEY` | Strong random secret for Remember-Me tokens | `<STRONG_64_CHAR_RANDOM_SECRET>` |
| `ALLOWED_ORIGINS` | Comma-separated allowed HTTPS origins for CORS | `https://quality.iast.com` |
| `SSL_CERTIFICATE` | PEM-encoded SSL certificate (with `\n` linebreaks) | `-----BEGIN CERTIFICATE-----\n...` |
| `SSL_CERTIFICATE_PRIVATE_KEY` | PEM-encoded SSL private key (with `\n` linebreaks) | `-----BEGIN PRIVATE KEY-----\n...` |
| `MAIL_HOST` | SMTP server host | `smtp.office365.com` |
| `MAIL_PORT` | SMTP server port | `587` |
| `MAIL_USERNAME` | SMTP authentication username | `quality-notifications@iast.com` |
| `MAIL_PASSWORD` | SMTP authentication app password | `<SMTP_APP_PASSWORD>` |
| `FEEDBACK_RECIPIENTS` | Comma-separated feedback email recipients | `quality-admin@iast.com` |
| `FEEDBACK_FROM_EMAIL` | Sender email address for feedback notifications | `no-reply@iast.com` |

---

## Production Deployment Workflow

1. **Configure Environment Variables**: Supply all production environment variables (database credentials, Remember-Me key, SSL certificates, SMTP credentials).
2. **Verify Database Connectivity**: Ensure MySQL database `quality_website` is accessible and populated.
3. **Configure SSL**: Supply valid PEM certificate and private key string content.
4. **Build Production Artifact**: Run `.\mvnw.cmd clean package -DskipTests`.
5. **Run the JAR**: Execute `java -jar target/quality-backend-0.0.1-SNAPSHOT.jar`.
6. **Verify HTTPS Termination**: Test browser access over HTTPS (`https://<DOMAIN>`) and verify HTTP port redirects.
7. **Verify Portal & Admin Login**: Log in to `/admin/login` using administrator credentials.
8. **Verify Public Document Access**: Confirm public document downloads stream from MySQL DMS storage and unregistered requests return 404.