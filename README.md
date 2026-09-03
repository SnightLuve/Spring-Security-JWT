# JWT API

Spring Boot API with access JWT, rotating refresh tokens, role-based authorization, SQL Server, HikariCP, and Swagger UI.

## Requirements

- JDK 17
- SQL Server 2019+ for the `dev` or `prod` profile

## Profiles

| Profile | Purpose | Schema strategy |
|---|---|---|
| `dev` (default) | Local development | Hibernate creates/updates tables |
| `prod` | Production | Hibernate validates an existing schema only |
| `test` | Automated tests | In-memory H2; no SQL Server needed |

## Configure SQL Server

Create an empty database named `jwt`, then set these variables before running. `DB_URL` must include the database name and suitable TLS options for your server.

```powershell
$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=jwt;encrypt=true;trustServerCertificate=true'
$env:DB_USERNAME = 'sa'
$env:DB_PASSWORD = 'your-strong-password'
$env:JWT_SECRET = 'replace-with-a-random-base64-secret-at-least-32-bytes'
```

Generate a safe Base64 secret in PowerShell:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

## Run

`dev` is the default profile:

```powershell
.\mvnw.cmd spring-boot:run
```

Run a specific profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
.\mvnw.cmd test
```

For production, set `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`. The application intentionally refuses to start without the JWT secret. Tune Hikari via `DB_POOL_MIN_IDLE` and `DB_POOL_MAX_SIZE`.

## CORS

The `dev` profile accepts requests only from `http://localhost:3000` and `http://localhost:5173`. Set a comma-separated list to permit other frontend origins:

```powershell
$env:CORS_ALLOWED_ORIGINS = 'https://app.example.com,https://admin.example.com'
```

The `prod` profile denies browser origins until `CORS_ALLOWED_ORIGINS` is explicitly set. Credentials/cookies are not allowed because this API authenticates with the `Authorization: Bearer` header.

## Redis cache

The application caches loaded users (including roles) in Redis for 10 minutes. This reduces SQL Server reads during JWT validation. Changing a user's roles clears that user's cache entry immediately.

The `dev` profile connects to `localhost:6379` by default. Override it as needed:

```powershell
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = '6379'
$env:REDIS_PASSWORD = 'your-redis-password'
$env:REDIS_DATABASE = '0'
```

For production, `REDIS_HOST` and `REDIS_PASSWORD` are required. Set `REDIS_SSL_ENABLED=true` when using TLS, and optionally set `REDIS_CACHE_TTL` (for example, `15m`). The test profile disables Redis cache.

## Authorization roles

Each user has a set of roles in the `user_roles` table. A new registration receives `ROLE_USER`. Spring Security checks role names through `hasRole('ADMIN')`, which corresponds to `ROLE_ADMIN` in the database.

To bootstrap the first administrator after registration:

```sql
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ADMIN' FROM users WHERE username = 'admin';
```

An authenticated administrator can manage another user's roles:

```http
PUT /api/admin/users/{username}/roles
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{ "roles": ["ROLE_USER", "ROLE_ADMIN"] }
```

Requests without a valid login receive JSON `401`; authenticated users lacking `ROLE_ADMIN` receive JSON `403`.

## Swagger

Start the application, then open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). Use **Authorize** and paste the access token (without `Bearer`). OpenAPI JSON is at `/v3/api-docs`.

## Authentication API

`POST /api/auth/register` and `POST /api/auth/login` accept:

```json
{ "username": "alice", "password": "password" }
```

Both return an access token and a refresh token. Send the access token as `Authorization: Bearer <token>`.

`POST /api/auth/refresh`:

```json
{ "refreshToken": "..." }
```

The refresh token is rotated on every successful refresh. `POST /api/auth/logout` requires an access token and invalidates the current refresh token.
