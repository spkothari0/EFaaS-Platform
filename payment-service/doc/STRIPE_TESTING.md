# Stripe CLI Testing Guide

## Prerequisites
- Docker installed and running
- Stripe CLI image pulled: `docker pull stripe/stripe-cli`

---

## Step 1 — Login

Run once to authenticate your Stripe account. Credentials are saved to a persistent Docker volume.

```bash
docker run --rm -it -v stripe-cli-config:/root/.config/stripe stripe/stripe-cli login
```

Opens a browser URL — click **Allow Access** to authenticate.

---

## Step 2 — Start Webhook Listener

Keep this running in a terminal while testing. Forwards Stripe webhook events to your local app.

```bash
docker run --rm -it -v stripe-cli-config:/root/.config/stripe stripe/stripe-cli listen --forward-to host.docker.internal:8082/webhooks/stripe
```

> Note: Copy the `whsec_...` signing secret printed here and put it in `application-dev.yml` under `stripe.webhook-secret` if you want signature verification enabled.

---

## Step 3 — Testing Payment Flow

### 3.1 Create a Payment
Call your API to create a payment:
```
POST http://localhost:8082/payments
```
Note the `paymentIntentId` (`pi_xxx...`) returned in the response.

---

### 3.2 Confirm Payment (Simulate Customer Paying)

**Success:**
```bash
docker run --rm -it -v stripe-cli-config:/root/.config/stripe stripe/stripe-cli payment_intents confirm <paymentIntentId> --payment-method pm_card_visa
```

**Declined:**
```bash
docker run --rm -it -v stripe-cli-config:/root/.config/stripe stripe/stripe-cli payment_intents confirm <paymentIntentId> --payment-method pm_card_visa_chargeDeclined
```

On success → webhook fires `payment_intent.succeeded` → status updated to `COMPLETED` in DB.
On decline → webhook fires `payment_intent.payment_failed` → status updated to `FAILED` in DB.

---

### 3.3 Refund a Payment

First retrieve the PaymentIntent to get the charge ID:
```bash
docker run --rm -it -v stripe-cli-config:/root/.config/stripe stripe/stripe-cli payment_intents retrieve <paymentIntentId>
```

Look for `latest_charge` field (`ch_xxx...`), then refund it:
```bash
docker run --rm -it -v stripe-cli-config:/root/.config/stripe stripe/stripe-cli refunds create --charge <chargeId>
```

Webhook fires `charge.refunded` → status updated to `REFUNDED` in DB.

> Note: PaymentIntent must be in `succeeded` state before refunding.

---

## Test Payment Methods

These are Stripe's predefined test IDs — only work in test mode (`sk_test_...`).

| ID | Behaviour |
|---|---|
| `pm_card_visa` | Success |
| `pm_card_visa_chargeDeclined` | Declined |
| `pm_card_mastercard` | Success |
| `pm_card_chargeDeclinedInsufficientFunds` | Declined - insufficient funds |
| `pm_card_chargeDeclinedExpiredCard` | Declined - expired card |
| `pm_card_chargeDeclinedFraudulent` | Declined - fraud |
| `pm_card_threeDSecure2Required` | Requires 3D Secure auth |

Full list: Stripe Dashboard → Developers → Testing

---

## Payment Status Lifecycle

```
POST /payments
      ↓
   CREATED
      ↓
   (stripe trigger / confirm)
      ↓
COMPLETED / FAILED / REFUNDED
```

| Status | Triggered by |
|---|---|
| `CREATED` | POST /payments API call |
| `COMPLETED` | Webhook: `payment_intent.succeeded` |
| `FAILED` | Webhook: `payment_intent.payment_failed` |
| `REFUNDED` | Webhook: `charge.refunded` |
