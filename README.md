# Nova Bank

[![CI](https://github.com/PW13052003/nova-bank/actions/workflows/ci.yml/badge.svg)](https://github.com/PW13052003/nova-bank/actions/workflows/ci.yml)

A full-stack banking application built from scratch to show how backend, database and security concerns fit together in a financial system.

**Live demo:** https://nova-bank-beta.vercel.app

| | |
|---|---|
| Email | `api-test@novabank.com` |
| Password | `SecurePass123` |

The demo account is shared and holds only test data. Please don't turn on two-factor for it, or the next visitor can't log in. To try two-factor, sign up your own account.

The backend runs on a free hosting tier that sleeps when idle, so the first request after a quiet period can take up to a minute.

## What it does

- Sign up, log in and log out
- Open checking and savings accounts
- Deposit, withdraw, and transfer between your own accounts
- Transaction history per account
- Optional two-factor login with an authenticator app (TOTP)

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3.4, plain JDBC |
| Database | PostgreSQL (Neon in production) |
| Frontend | Next.js 16, TypeScript, Tailwind CSS |
| Hosting | Render (backend, Docker), Vercel (frontend), Neon (database) |
| Tests | JUnit 5, Spring MockMvc |
| CI | GitHub Actions |

## Design decisions

### Ledger

- **Double-entry.** Every balance is the sum of that account's `ledger_entries`. There is no mutable balance column to drift out of sync.
- **Integer money.** Amounts are stored as `amount_cents BIGINT`, never as floating point.
- **Idempotency.** Each transaction carries a unique `idempotency_key`, so a repeated request can't move money twice.
- **Snapshots.** Each ledger entry stores `balance_after` for fast statements.
- **Atomic.** A deposit, withdrawal or transfer writes the transaction, the ledger entries and the audit row in one database transaction. If any part fails, none of it is saved.

### Concurrency

- Withdrawals and transfers lock the affected account rows with `SELECT ... FOR UPDATE` before checking the balance.
- Transfers lock accounts in a fixed order (by account ID), so two opposite transfers can't deadlock.
- The test suite covers this directly: ten simultaneous withdrawals against a $50 balance produce exactly five successes, five rejections and a final balance of zero. The same test fails when the lock is removed, which was verified.

### Security

- **Passwords:** BCrypt with cost factor 12. Plain text is never stored.
- **Sessions:** a JWT in an `HttpOnly`, `Secure` cookie that expires after 15 minutes. The frontend JavaScript never sees the token.
- **Token scopes:** session tokens and "password verified, code pending" tokens carry different scope claims. A pending token is rejected everywhere except the code check, so a password alone can't produce a session for a user with two-factor on.
- **Account lockout:** five failed attempts lock the account. For two-factor users a correct password doesn't reset the counter, only a correct code does, and each code attempt is counted atomically before it is checked, so parallel guessing is capped.
- **Rate limiting:** login, signup and code verification are limited per client. The limiter reads the forwarded address chain, which matters because requests pass through Vercel and Cloudflare before reaching the app.
- **Ownership checks:** every money endpoint verifies that the account belongs to the logged-in user.
- **Queries:** parameterized throughout.
- **Audit log:** logins, failed logins, lockouts, two-factor events, and every completed deposit, withdrawal and transfer are recorded.

## Project structure

```
nova-bank/
  .github/     CI workflow
  backend/     Spring Boot API, Dockerfile, tests
  frontend/    Next.js app
  database/    schema.sql
```

## API overview

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/signup` | Create a user and start a session |
| POST | `/auth/login` | Check the password; returns a session, or `twoFactorRequired` |
| POST | `/auth/2fa/verify` | Check the 6-digit code and start a session |
| POST | `/auth/logout` | Clear cookies |
| POST | `/2fa/setup` | Create a two-factor secret (requires a session) |
| POST | `/2fa/confirm` | Turn on two-factor with password and code (requires a session) |
| GET, POST | `/accounts` | List or create accounts |
| POST | `/deposit`, `/withdraw`, `/transfer` | Move money |
| GET | `/accounts/{id}/transactions` | Transaction history |

## Local setup

You need Java 21 or newer, Maven, Node.js, and PostgreSQL.

### Database

```bash
createdb novabank
psql novabank -f database/schema.sql
```

### Backend

1. Copy `backend/src/main/resources/application.properties.example` to `application.properties` in the same folder. The copy is git-ignored.
2. Set the environment variables and start the server:

```bash
cd backend
export DATABASE_URL="jdbc:postgresql://localhost:5432/novabank"
export DATABASE_USERNAME="your_db_user"
export DATABASE_PASSWORD="your_db_password"
export JWT_SECRET="a-random-string-of-at-least-32-characters"
mvn spring-boot:run
```

The API listens on port 8080.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The app opens on `http://localhost:3000` and forwards `/api` requests to `http://localhost:8080`. To point it elsewhere, set `BACKEND_URL` before starting.

Session cookies are marked `Secure`. Chrome and Firefox accept them on `http://localhost`, but Safari does not, so use Chrome or Firefox for local development.

## Tests

The tests write to the database and delete rows, so they run against a separate database, never your development one.

```bash
createdb novabank_test
psql novabank_test -f database/schema.sql
cd backend
mvn test
```

The Maven configuration points the tests at `novabank_test` automatically, and the test base class refuses to run against any database whose name doesn't contain `novabank_test`. The suite has 59 tests covering authentication, the ledger, concurrency, audit logging, token scopes, two-factor login, and the rate limiter.

## Continuous integration

A GitHub Actions workflow runs on every push to `main` and on pull requests. The backend job starts a PostgreSQL container, loads the schema into a test database and runs the full test suite. The frontend job installs dependencies from the lockfile and runs a production build, which includes type checking.

## Deployment

| Service | Role | Configuration |
|---|---|---|
| Neon | PostgreSQL | Load `database/schema.sql` |
| Render | Backend (Docker, from `backend/Dockerfile`) | `DATABASE_URL` (JDBC form, no credentials in the URL), `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `FRONTEND_URL` |
| Vercel | Frontend (root directory `frontend`) | `BACKEND_URL` set to the Render URL |

The frontend calls `/api/...` on its own domain and Vercel forwards those requests to the backend. This keeps the browser talking to a single site, so the session cookie works in browsers that block third-party cookies.

## Known limitations

- Two-factor can't be turned off from the app, and there are no recovery codes. A user who loses their authenticator is locked out.
- The two-factor secret is stored unencrypted in the database.
- A valid code can be reused during its roughly 30 to 90 second window.
- The password check is not atomic across parallel requests the way the code check is. BCrypt cost and rate limiting make this hard to exploit, but it is a gap.
- Rate-limit counters live in memory and reset when the server restarts.
- There are no refresh tokens. Sessions last 15 minutes, then the user logs in again.
- The free hosting tiers sleep when idle, and the free Neon database expires if not upgraded.
- Transfers are limited to a user's own accounts.
- Backend deploys are triggered manually on Render, and CI does not deploy.
- There are no frontend tests. CI only type-checks and builds the frontend.
- `npm audit` reports vulnerabilities in frontend dependencies that haven't been investigated.