# Nova Bank

Full-stack banking system built end to end to learn how backend, database, and security concerns fit together in a real financial system.

## Stack

- Backend: Java (starting with plain JDBC, evaluating Spring later)
- Database: PostgreSQL
- Frontend: TypeScript, Next.js, Tailwind

## Structure

```
bank-app/
  backend/     Java backend
  frontend/    Next.js frontend
  database/    SQL schema
```

## Local setup

### Database

1. Create a local Postgres database named `bankapp`.
2. Run `database/schema.sql` against it.

### Backend

1. Copy `backend/src/main/resources/application.properties.example` to `application.properties` in the same folder.
2. Fill in your local Postgres credentials.
3. Build with Maven: `mvn clean install` from inside `backend/`.

### Frontend

1. See `frontend/README.md` for scaffold instructions.

## Status

Environment setup only. No application logic yet.
