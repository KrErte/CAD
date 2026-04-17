# Auth + billing setup

## 1. Google OAuth

1. Mine https://console.cloud.google.com → APIs & Services → Credentials → Create OAuth client ID
2. Type: Web application
3. Authorized redirect URIs:
   - `http://localhost:8080/login/oauth2/code/google` (dev)
   - `https://ai-cad.ee/login/oauth2/code/google` (prod, kui domeen on olemas)
4. Lisa `.env`-sse:
   ```
   GOOGLE_CLIENT_ID=...
   GOOGLE_CLIENT_SECRET=...
   FRONTEND_URL=http://localhost:4200
   ```

## 2. JWT secret

```
JWT_SECRET=$(openssl rand -base64 48)
```

## 3. Postgres

`docker compose up -d db` — Flyway ajab V1__init.sql automaatselt.

## 4. Stripe (sinu pool)

Kui saad võtmed valmis:
1. Loo Stripe Products + Prices: `Pro Monthly — 4.99 €/month` ja `Pro Yearly — 49 €/year`
2. Webhook endpoint: `https://ai-cad.ee/api/stripe/webhook`
3. Kuula event'e: `checkout.session.completed`, `invoice.paid`, `customer.subscription.deleted`
4. Võta whsec_... ja pane `.env`-sse: `STRIPE_WEBHOOK_SECRET=whsec_...`
5. Frontend'is: kasuta Stripe Checkout JS SDK, pane `client_reference_id: user.id` — nii me lingime Stripe customer'i tagasi meie user'ile.

Backend juba teab: checkout → PRO, invoice.paid → extend, sub.deleted → FREE.

## 5. Quota

`FREE_MONTHLY_QUOTA=3` (default). PRO = piiramatu.

## 6. Endpoints

- `GET /oauth2/authorization/google` → redirect Google'isse
- `GET /login/oauth2/code/google` → callback, redirect frontendi `#/auth?token=JWT`
- `GET /api/me` (Bearer) → `{ email, plan, used, limit }`
- `POST /api/spec` (Bearer) — parsib prompt'i
- `POST /api/generate` (Bearer) — konsumeerib 1 STL-i quota'st
- `POST /api/stripe/webhook` — Stripe events
