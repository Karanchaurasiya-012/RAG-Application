# AuthFrontend

Next.js App Router SPA for the AuthBackend cookie + CSRF auth API.

## Setup

```bash
cp .env.example .env.local
npm install
npm run dev
```

`NEXT_PUBLIC_API_BASE_URL` defaults to `http://localhost:8080`.

## Routes

| Path | Purpose |
| --- | --- |
| `/` | Landing |
| `/login` | Email/password + Google OAuth |
| `/register` | Create account |
| `/verify-email?token=` | Email verification |
| `/forgot-password` | Request reset email |
| `/reset-password?token=` | Set new password |
| `/dashboard` | Authenticated profile + logout |

All API calls use `credentials: "include"` and CSRF from `GET /api/v1/auth/csrf`.
