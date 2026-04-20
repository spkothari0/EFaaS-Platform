# EFaaS End-to-End Testing Guide

Complete walkthrough to bring the platform to a testable state — from cold infrastructure to a fully active loan with repayment tracking.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Step 0 — Infrastructure Setup](#step-0--infrastructure-setup)
- [Step 1 — Create a Tenant](#step-1--create-a-tenant)
- [Step 2 — Generate an API Key](#step-2--generate-an-api-key)
- [Step 3 — Link a Bank Account via Plaid](#step-3--link-a-bank-account-via-plaid)
- [Step 4 — Create a Stripe Payment](#step-4--create-a-stripe-payment)
- [Step 5 — Apply for a Loan](#step-5--apply-for-a-loan)
- [Step 6 — Get the Amortization Schedule](#step-6--get-the-amortization-schedule)
- [Step 7 — Disburse the Loan](#step-7--disburse-the-loan)
- [Step 8 — Simulate Loan Repayment](#step-8--simulate-loan-repayment)
- [Step 9 — Verify State in the Database](#step-9--verify-state-in-the-database)
- [Dependency Chain Summary](#dependency-chain-summary)

---

## Prerequisites

**Nothing needs to be pre-inserted into the database.** Hibernate auto-creates all tables on first startup via `ddl-auto: update`. The Tenant creation call in Step 1 seeds all subsequent data.

You will need:
- Docker Desktop running
- Stripe account (test mode) — [stripe.com/dashboard](https://stripe.com/dashboard)
- Plaid account (sandbox) — [dashboard.plaid.com](https://dashboard.plaid.com)
- `stripe` CLI installed locally
- `jq` installed for pretty-printing responses (optional)

### Environment Variables

Set these before starting the services:

```bash
export STRIPE_SECRET_KEY=sk_test_YOUR_KEY           # Stripe dashboard → Developers → API keys
export STRIPE_WEBHOOK_SECRET=whsec_YOUR_SECRET      # Set after Step 0.3 below
export PLAID_CLIENT_ID=YOUR_CLIENT_ID               # Plaid dashboard → Team Settings → Keys
export PLAID_SECRET=YOUR_SANDBOX_SECRET             # Plaid sandbox secret
export PLAID_ENV=sandbox
export PLAID_WEBHOOK_URL=http://localhost:8082/webhooks/plaid
```

---

## Step 0 — Infrastructure Setup

### 0.1 Start Docker Infrastructure

From the project root:

```bash
docker-compose up -d postgres-tenant postgres-payment postgres-lending redis kafka zookeeper
```

Wait ~20 seconds for all containers to pass their healthchecks before starting the services.

### 0.2 Forward Stripe Webhooks

Open a dedicated terminal and run:

```bash
stripe listen --forward-to localhost:8082/webhooks/stripe
```

Copy the `whsec_...` secret it prints and set it as `STRIPE_WEBHOOK_SECRET` before starting Payment Service.

### 0.3 Start All Microservices

Start each service in a separate terminal, **in this order**:

```bash
# Terminal 1 — Tenant Service (port 8081)
cd tenant-service && mvn spring-boot:run

# Terminal 2 — Payment Service (port 8082)
cd payment-service && mvn spring-boot:run \
  -DSTRIPE_SECRET_KEY=$STRIPE_SECRET_KEY \
  -DSTRIPE_WEBHOOK_SECRET=$STRIPE_WEBHOOK_SECRET \
  -DPLAID_CLIENT_ID=$PLAID_CLIENT_ID \
  -DPLAID_SECRET=$PLAID_SECRET

# Terminal 3 — Lending Service (port 8083)
cd lending-service && mvn spring-boot:run

# Terminal 4 — API Gateway (port 8080)
cd gateway && mvn spring-boot:run
```

All API calls below go through the **gateway on port 8080**.

---

## Step 1 — Create a Tenant

No pre-existing data required. This is always the first call.

```bash
curl -s -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Corp",
    "email": "test@testcorp.com",
    "plan": "BASIC"
  }' | jq .
```

Expected response (`201 Created`):
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "name": "Test Corp",
  "email": "test@testcorp.com",
  "plan": "BASIC",
  "active": true
}
```

Save the tenant ID:
```bash
TENANT_ID="<id from response>"
```

---

## Step 2 — Generate an API Key

```bash
curl -s -X POST http://localhost:8080/api/v1/tenants/$TENANT_ID/api-keys | jq .
```

Expected response (`201 Created`):
```json
{
  "id": "...",
  "tenantId": "...",
  "key": "efaas_live_...",
  "maskedKey": "efaas_live_4Z8w...S0T1",
  "active": true
}
```

> **The `key` value is shown only once.** Store it securely.

Save the API key:
```bash
API_KEY="efaas_live_..."
```

Every subsequent request through the gateway requires the `X-API-Key: $API_KEY` header. The gateway validates it against the Tenant Service, caches the result in Redis for 5 minutes, and injects `X-Tenant-Id` into the downstream request.

---

## Step 3 — Link a Bank Account via Plaid

A linked Plaid account is required before applying for a loan — the credit scoring engine reads live balance and 90-day transaction history from it.

### 3.1 Create a Link Token

```bash
curl -s -X POST http://localhost:8080/api/v1/plaid/link-token \
  -H "X-API-Key: $API_KEY" | jq .
```

In production this token is passed to the Plaid Link UI. In sandbox, skip to 3.2.

### 3.2 Get a Sandbox Public Token (no UI required)

Call Plaid's sandbox utility endpoint directly to get a fake public token:

```bash
curl -s -X POST https://sandbox.plaid.com/sandbox/public_token/create \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "'$PLAID_CLIENT_ID'",
    "secret": "'$PLAID_SECRET'",
    "institution_id": "ins_109508",
    "initial_products": ["auth", "transactions", "balance"]
  }' | jq .
```

Save `public_token` from the response.

### 3.3 Exchange the Public Token

```bash
curl -s -X POST http://localhost:8080/api/v1/plaid/exchange-token \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"publicToken": "<public_token from 3.2>"}' | jq .
```

### 3.4 List Linked Accounts

```bash
curl -s http://localhost:8080/api/v1/plaid/accounts \
  -H "X-API-Key: $API_KEY" | jq .
```

Save a `plaidAccountId` from the response:
```bash
ACCOUNT_ID="<plaidAccountId from response>"
```

---

## Step 4 — Create a Stripe Payment

Tests the card payment flow and primes the Kafka event chain for loan repayment matching.

### 4.1 Create a PaymentIntent

```bash
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 5000,
    "currency": "usd",
    "description": "Test payment",
    "idempotencyKey": "test-pay-001"
  }' | jq .
```

Save the Stripe PaymentIntent ID from the response:
```bash
INTENT_ID="pi_..."
```

### 4.2 Confirm the Payment (Stripe sandbox)

```bash
curl -s https://api.stripe.com/v1/payment_intents/$INTENT_ID/confirm \
  -u $STRIPE_SECRET_KEY: \
  -d "payment_method=pm_card_visa" | jq .
```

The Stripe CLI webhook forwarder delivers `payment_intent.succeeded` to Payment Service → Payment Service publishes `PaymentCompletedEvent` to `payments-topic` → Lending Service consumer picks it up.

---

## Step 5 — Apply for a Loan

The Lending Service will:
1. Call Payment Service `GET /internal/financial-profile/{accountId}` to fetch Plaid balance and transactions
2. Run the `CreditScoringEngine` on the `FinancialProfile`
3. Return a decision with reason

```bash
curl -s -X POST http://localhost:8080/api/v1/loans/apply \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "applicantUserId": "user-001",
    "plaidAccountId": "'$ACCOUNT_ID'",
    "principalAmountCents": 500000,
    "termMonths": 12,
    "annualInterestRate": 8.5,
    "purpose": "Home improvement"
  }' | jq .
```

Expected response fields:

| Field | Description |
|---|---|
| `status` | `APPROVED` or `DENIED` |
| `creditScore` | Numeric score from the engine |
| `decisionReason` | Human-readable explanation |
| `monthlyPaymentCents` | Calculated installment (if approved) |
| `id` | Loan UUID |

Save the loan ID:
```bash
LOAN_ID="<id from response>"
```

> **If the loan is DENIED in sandbox**: The default Plaid sandbox institution (`ins_109508`) has a ~$110 checking balance, which may be too low for large loan amounts. Try reducing `principalAmountCents` to `100000` ($1,000) or use a Plaid sandbox institution with higher balances.

---

## Step 6 — Get the Amortization Schedule

```bash
curl -s http://localhost:8080/api/v1/loans/$LOAN_ID/schedule \
  -H "X-API-Key: $API_KEY" | jq .
```

Returns all `RepaymentInstallment` records with `dueDate`, `principalCents`, `interestCents`, `totalCents`, and `status` (`UNPAID` initially).

---

## Step 7 — Disburse the Loan

The loan must be in `APPROVED` status. Disbursement moves it through `DISBURSED` → `ACTIVE`.

```bash
curl -s -X POST http://localhost:8080/api/v1/loans/$LOAN_ID/disburse \
  -H "X-API-Key: $API_KEY" | jq .
```

---

## Step 8 — Simulate Loan Repayment

The Lending Service's `PaymentEventConsumer` listens on `payments-topic`. When a `PaymentCompletedEvent` arrives, it matches the Stripe PaymentIntent ID to a `RepaymentInstallment` and marks it `PAID`.

### 8.1 Create the Repayment Payment

Use the `monthlyPaymentCents` value from Step 5:

```bash
curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": <monthlyPaymentCents>,
    "currency": "usd",
    "description": "Loan repayment",
    "idempotencyKey": "repay-loan-001-installment-1"
  }' | jq .
```

### 8.2 Confirm the Payment

```bash
curl -s https://api.stripe.com/v1/payment_intents/<new_INTENT_ID>/confirm \
  -u $STRIPE_SECRET_KEY: \
  -d "payment_method=pm_card_visa" | jq .
```

The webhook fires → Lending Service updates the matching installment status to `PAID`.

Verify by re-fetching the schedule:

```bash
curl -s http://localhost:8080/api/v1/loans/$LOAN_ID/schedule \
  -H "X-API-Key: $API_KEY" | jq '.[0]'
```

The first installment should now show `"status": "PAID"`.

---

## Step 9 — Verify State in the Database

Connect via pgAdmin at [http://localhost:5050](http://localhost:5050) using `admin@efaas.com` / `admin`.

To add a server in pgAdmin: **Servers → Register → Server**, then fill in the Connection tab:

| Service | Host name | Port | Database | Username | Password |
|---|---|---|---|---|---|
| Tenant | `efaas-postgres-tenant` | `5432` | `tenants_db` | `efaas` | `dev_password` |
| Payment | `efaas-postgres-payment` | `5432` | `payments_db` | `efaas` | `dev_password` |
| Lending | `efaas-postgres-lending` | `5432` | `lending_db` | `efaas` | `dev_password` |

Key tables to inspect:

| Database | Table | What to verify |
|---|---|---|
| `tenants_db` | `tenant` | Tenant row exists and `active = true` |
| `tenants_db` | `api_key` | Key row with `key_hash` (SHA-256, never plaintext) |
| `payments_db` | `plaid_item` | Linked institution row |
| `payments_db` | `plaid_account` | Account rows under the item |
| `payments_db` | `payment` | PaymentIntent rows with `status = COMPLETED` |
| `payments_db` | `webhook_event` | Stripe webhook event rows |
| `lending_db` | `loan` | Loan row with `status = ACTIVE` after disbursement |
| `lending_db` | `repayment_installment` | All installments; first one `status = PAID` after Step 8 |

---

## Dependency Chain Summary

```
[Docker: PostgreSQL × 3 + Kafka + Redis]
        │
        ▼
[Tenant Service :8081]
  POST /api/v1/tenants          → creates Tenant row
  POST /api/v1/tenants/{id}/api-keys → creates ApiKey row
        │
        ▼ (API_KEY required from here on — Gateway validates + caches in Redis)
        │
[Payment Service :8082]
  POST /api/v1/plaid/link-token
  POST /api/v1/plaid/exchange-token  → creates PlaidItem + PlaidAccount rows
  POST /api/v1/payments              → creates Payment row via Stripe
        │                                      │
        │                         [Stripe webhook → payments-topic]
        │                                      │
        ▼                                      ▼
[Lending Service :8083]            [Lending Service Kafka Consumer]
  POST /api/v1/loans/apply           PaymentEventConsumer
    → HTTP GET /internal/             matches PaymentIntent ID
      financial-profile/{accountId}  to RepaymentInstallment
      (calls Payment Service)        marks installment PAID
    → CreditScoringEngine
    → creates Loan row
  POST /api/v1/loans/{id}/disburse
    → Loan status: APPROVED → ACTIVE
```
