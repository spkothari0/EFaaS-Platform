# Plaid Integration Flow — EFaaS Payment Service

**Characters in this example:**
- **Alex Smith** — end user who wants to link his bank account to EFaaS
- **Chase** — his bank (institution ID `ins_3` in Plaid's system)
- **EFaaS** — your platform (the Plaid API client)

---

## The Two Major Phases

```
Phase 1 ──── Bank Linking ────► Who is Alex? What accounts does he have?
Phase 2 ──── ACH Payment  ────► Debit Alex's Chase checking for $50.00
Phase 3 ──── Webhooks     ────► Plaid tells you what happened to the transfer
```

---

## Phase 1 — Bank Account Linking

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────┐         ┌───────┐
│ Alex's      │         │ EFaaS            │         │ Plaid       │         │ Chase │
│ Browser     │         │ payment-service  │         │ API         │         │ Bank  │
└──────┬──────┘         └────────┬─────────┘         └──────┬──────┘         └───┬───┘
       │                         │                           │                    │
       │  POST /link-token       │                           │                    │
       │  { userId: "alex-123" } │                           │                    │
       │────────────────────────►│                           │                    │
       │                         │  linkTokenCreate()        │                    │
       │                         │──────────────────────────►│                    │
       │                         │  ◄── link_token           │                    │
       │  ◄── link_token         │                           │                    │
       │                         │                           │                    │
       │  [Opens Plaid Link UI]  │                           │                    │
       │  Alex picks "Chase"     │                           │                    │
       │  Enters his credentials │                           │                    │
       │─────────────────────────────────────────────────────────────────────────►│
       │                         │                           │  Auth + MFA        │
       │                         │                           │◄────────────────── │
       │  ◄── public_token       │                           │                    │
       │                         │                           │                    │
       │  POST /exchange-token   │                           │                    │
       │  { publicToken: "..." } │                           │                    │
       │────────────────────────►│                           │                    │
       │                         │  itemPublicTokenExchange()│                    │
       │                         │──────────────────────────►│                    │
       │                         │  ◄── access_token         │                    │
       │                         │      item_id              │                    │
       │                         │                           │                    │
       │                         │  itemGet()                │                    │
       │                         │──────────────────────────►│                    │
       │                         │  ◄── institution_id       │                    │
       │                         │                           │                    │
       │                         │  institutionsGetById()    │                    │
       │                         │──────────────────────────►│                    │
       │                         │  ◄── "Chase"              │                    │
       │                         │                           │                    │
       │                         │  accountsGet()            │                    │
       │                         │──────────────────────────►│◄── account list ───│
       │                         │  ◄── accounts []          │                    │
       │                         │                           │                    │
       │                         │  [SAVE TO DB]             │                    │
       │                         │  [PUBLISH KAFKA EVENT]    │                    │
       │  ◄── linked accounts    │                           │                    │
```

### Step 1 — Create Link Token

**API call your frontend makes:**
```
POST /api/v1/plaid/link-token
X-Tenant-Id: <tenant-uuid>
{ "userId": "alex-123" }
```

**Code:** `PlaidController:33` → `PlaidService:49`

What happens in `PlaidService.createLinkToken()`:
```java
LinkTokenCreateRequest request = new LinkTokenCreateRequest()
    .user(new LinkTokenCreateRequestUser().clientUserId("alex-123"))
    .clientName("EFaaS Platform")
    .products(List.of(Products.TRANSACTIONS, Products.AUTH))
    .countryCodes(List.of(CountryCode.US))
    .webhook(webhookUrl);       // ← tells Plaid where to send events later
```

- Plaid creates a temporary session for Alex
- Returns a `link_token` valid for ~30 minutes
- **Nothing is saved to DB** — this token is stateless

**Response:**
```json
{ "linkToken": "link-sandbox-abc123...", "expiration": "2026-04-11T10:30:00Z" }
```

---

### Step 2 — Alex Logs Into Chase (Plaid Link UI)

**Your backend is not involved here at all.**

The frontend opens Plaid's embedded Link widget using the `link_token`. Plaid handles:
1. Showing Alex a list of banks
2. Alex selects Chase
3. Alex enters Chase username + password
4. Chase sends MFA code to Alex's phone
5. Alex enters the code → Plaid authenticates with Chase

When done, Plaid gives the frontend a short-lived `public_token` (30 min TTL).

**Plaid Dashboard at this point:** An "Item" record is created representing Alex ↔ Chase,
but it is in a temporary state until step 3 completes.

---

### Step 3 — Exchange Public Token (The Important One)

**API call your frontend makes:**
```
POST /api/v1/plaid/exchange-token
X-Tenant-Id: <tenant-uuid>
{ "publicToken": "public-sandbox-xyz..." }
```

**Code:** `PlaidController:49` → `PlaidService:79`

Four Plaid API calls happen in sequence:

```
Call 1 — itemPublicTokenExchange
  IN:  public_token (short-lived, one-time use)
  OUT: access_token  "access-sandbox-abc..."  ← permanent, store this
       item_id       "item-sandbox-111..."     ← Plaid's ID for this bank connection

Call 2 — itemGet
  IN:  access_token
  OUT: institution_id  "ins_3"

Call 3 — institutionsGetById
  IN:  institution_id  "ins_3"
  OUT: institution_name  "Chase"

Call 4 — accountsGet
  IN:  access_token
  OUT: [
         { accountId: "acc-aaa", name: "Total Checking", mask: "1234",
           type: "depository", subtype: "checking" },
         { accountId: "acc-bbb", name: "Chase Savings",  mask: "5678",
           type: "depository", subtype: "savings" }
       ]
```

**DB writes:**

`plaid_items` — one row per bank connection:
```
id:               <internal uuid>
tenant_id:        <your-tenant-uuid>
plaid_item_id:    "item-sandbox-111..."
access_token:     "access-sandbox-abc..."    ← TODO: encrypt at rest (see PlaidItem.java:36)
institution_id:   "ins_3"
institution_name: "Chase"
status:           "ACTIVE"
```

`plaid_accounts` — one row per account Alex linked:
```
Row 1:
  plaid_account_id: "acc-aaa"
  name:             "Total Checking"
  mask:             "1234"
  type:             "depository"
  subtype:          "checking"
  active:           true
  plaid_item_id:    → FK to the plaid_items row above

Row 2:
  plaid_account_id: "acc-bbb"
  name:             "Chase Savings"
  mask:             "5678"
  type:             "depository"
  subtype:          "savings"
  active:           true
```

**Kafka event published:**
```java
BankAccountLinkedEvent {
    tenantId:        <your-tenant-uuid>,
    plaidItemId:     "item-sandbox-111...",
    institutionId:   "ins_3",
    institutionName: "Chase",
    accountCount:    2
}
```

**Plaid Dashboard after this step:** The Item moves to "Active" status. Chase's name and 
Alex's account list appear in the Item detail view.

---

### Other Account Operations

**List Alex's linked accounts** — reads from DB only, no Plaid call:
```
GET /api/v1/plaid/accounts
X-Tenant-Id: <tenant-uuid>
```
Code: `PlaidService:178` → `plaidAccountRepository.findActiveByTenantId()`

**Unlink an account** — `PlaidService:191`:
```
DELETE /api/v1/plaid/accounts/{accountId}
X-Tenant-Id: <tenant-uuid>
```
- Sets `plaid_accounts.active = false`
- If it was the **last** active account for this Item:
  - Calls Plaid `itemRemove()` → revokes the `access_token` on Plaid's side
  - Sets `plaid_items.status = "REMOVED"`
- Plaid Dashboard: Item disappears (or shows as removed)

---

## Phase 2 — ACH Payment (Debit Alex $50.00)

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────┐
│ Alex's      │         │ EFaaS            │         │ Plaid       │
│ Browser     │         │ payment-service  │         │ Transfer API│
└──────┬──────┘         └────────┬─────────┘         └──────┬──────┘
       │                         │                           │
       │  POST /payments/ach     │                           │
       │  { accountId, amount }  │                           │
       │────────────────────────►│                           │
       │                         │  [lookup PlaidAccount]    │
       │                         │  [lookup PlaidItem]       │
       │                         │  [get access_token]       │
       │                         │                           │
       │                         │  transferAuthorizationCreate()
       │                         │──────────────────────────►│
       │                         │  ◄── APPROVED             │
       │                         │      authorization_id     │
       │                         │                           │
       │                         │  transferCreate()         │
       │                         │──────────────────────────►│
       │                         │  ◄── transfer_id          │
       │                         │                           │
       │                         │  [SAVE AchPayment PENDING]│
       │                         │  [PUBLISH KAFKA EVENT]    │
       │  ◄── payment response   │                           │
```

**API call:**
```
POST /api/v1/plaid/payments/ach
X-Tenant-Id: <tenant-uuid>
{
  "accountId":         "<uuid of the PlaidAccount row — your internal ID>",
  "amount":            5000,
  "currency":          "USD",
  "accountHolderName": "Alex Smith",
  "idempotencyKey":    "payment-efaas-2026-001",
  "description":       "EFaaS Monthly Fee"
}
```

**Code:** `PlaidController:82` → `PlaidService:232`

**Idempotency check first** (`PlaidService:234`): If a record already exists with this 
`idempotencyKey`, return it immediately without calling Plaid again. Safe to retry.

Then two Plaid API calls:

```
Call 1 — transferAuthorizationCreate
  Purpose: Plaid's risk engine decides if the transfer is safe to proceed.
           It checks account history, balance signals, fraud patterns.
  IN:  access_token, accountId: "acc-aaa"
       type: DEBIT, network: ACH, amount: "50.00"
       achClass: WEB, user: { legalName: "Alex Smith" }
  OUT: decision:        APPROVED   (or DECLINED → throws PlaidException)
       authorizationId: "auth-zzz..."

Call 2 — transferCreate
  Purpose: Actually schedule the ACH debit.
  IN:  access_token, accountId: "acc-aaa"
       authorizationId: "auth-zzz..."
       amount: "50.00", description: "EFaaS Monthly Fee"
  OUT: transferId: "transfer-ttt..."
```

**DB write — `ach_payments` table:**
```
id:                     <internal uuid>
tenant_id:              <your-tenant-uuid>
plaid_account_db_id:    <uuid of PlaidAccount row>
plaid_transfer_id:      "transfer-ttt..."
plaid_authorization_id: "auth-zzz..."
idempotency_key:        "payment-efaas-2026-001"
amount:                 5000        ← stored in cents
currency:               "USD"
description:            "EFaaS Monthly Fee"
status:                 PENDING     ← starts here, webhook moves it forward
```

**Kafka event published:**
```java
AchPaymentInitiatedEvent {
    tenantId:       <your-tenant-uuid>,
    paymentId:      <internal uuid>,
    plaidTransferId: "transfer-ttt...",
    amount:         5000,
    currency:       "USD"
}
```

**Plaid Dashboard:** Transfer appears with status `pending`. The ACH file has not yet
been submitted to the Federal Reserve — that happens overnight in Plaid's batch processing.

---

## Phase 3 — Webhooks Update the Payment Status

ACH takes 1–3 business days to settle. Plaid pushes status changes to your webhook URL.

```
┌─────────────┐         ┌──────────────────────┐         ┌─────────────┐
│ Plaid       │         │ EFaaS                │         │ DB          │
│ Servers     │         │ /webhooks/plaid      │         │ ach_payments│
└──────┬──────┘         └──────────┬───────────┘         └──────┬──────┘
       │                           │                             │
       │  POST /webhooks/plaid     │                             │
       │  { webhook_type: "TRANSFER",                            │
       │    webhook_code: "TRANSFER_EVENTS_PENDING" }            │
       │──────────────────────────►│                             │
       │                           │  transferEventSync()        │
       │◄──────────────────────────│                             │
       │  events: [{               │                             │
       │    transferId: "transfer-ttt...",                        │
       │    eventType:  "posted"   │                             │
       │  }]                       │                             │
       │──────────────────────────►│                             │
       │                           │  findByPlaidTransferId()    │
       │                           │────────────────────────────►│
       │                           │  ◄── AchPayment (PENDING)   │
       │                           │  update status = POSTED     │
       │                           │────────────────────────────►│
       │  200 OK                   │                             │
       │◄──────────────────────────│                             │
```

### Webhook: `TRANSFER / TRANSFER_EVENTS_PENDING`

**Code:** `PlaidWebhookController:29` → `PlaidWebhookService:86`

Plaid does NOT tell you which transfer changed — it just knocks and says "go check".
Your code calls `transferEventSync` to pull all new events since the last one processed.

```java
// PlaidWebhookService:93
TransferEventSyncRequest syncRequest = new TransferEventSyncRequest()
    .afterId(lastTransferEventId);   // cursor — only fetch events you haven't seen
```

Events returned might look like:
```
[
  { eventId: 42, transferId: "transfer-ttt...", eventType: "posted"    },
  { eventId: 43, transferId: "transfer-uuu...", eventType: "failed"    },
  { eventId: 44, transferId: "transfer-vvv...", eventType: "cancelled" }
]
```

For each event, the status mapping (`PlaidWebhookService:126`):

| Plaid `eventType` | `AchPaymentStatus` before | After | Meaning |
|---|---|---|---|
| `pending`   | —       | (skipped)     | Transfer submitted, ACH not sent yet |
| `posted`    | PENDING | **POSTED**    | Money moved. ACH settled successfully |
| `failed`    | PENDING | **FAILED**    | Bank rejected (NSF, closed account, etc.) |
| `cancelled` | PENDING | **CANCELLED** | Transfer was cancelled before settling |

The cursor advances: `lastTransferEventId = 44`, so next webhook call only fetches events > 44.

> **Important limitation in current code (`PlaidWebhookService:33`):**
> `lastTransferEventId` is an in-memory `volatile int`. If the service restarts, the cursor 
> resets to 0 and replays all events. The TODO comment says to persist this in DB — that
> would be a `plaid_sync_cursors` table or a single-row config table.

---

### Webhook: `ITEM / ERROR`

Fires when Alex's Chase connection breaks (e.g., he changed his Chase password).

```json
{
  "webhook_type": "ITEM",
  "webhook_code": "ERROR",
  "item_id": "item-sandbox-111..."
}
```

**Code:** `PlaidWebhookService:59`

**DB update — `plaid_items` table:**
```
status: "ACTIVE"  →  "ERROR"
```

No Plaid API call — just mark it locally. Alex must go through the Link flow again.
Any future `transferCreate` calls using this item's `access_token` will fail at Plaid.

---

### Webhook: `ITEM / PENDING_EXPIRATION`

Fires before an access token expires (rare, usually for tokens with expiry set).

**Code:** `PlaidWebhookService:74` — currently just logs a warning. 
In production you'd notify Alex via email/push to re-link his account before it expires.

---

## Complete Flow Summary

```
STEP  YOUR API ENDPOINT              PLAID CALLS MADE                 DB CHANGES
───── ──────────────────────────     ──────────────────────────────── ─────────────────────────────
 1    POST /link-token               linkTokenCreate()                 (none)

 2    [Browser ↔ Plaid Link UI]      (Plaid ↔ Chase directly)         (none — Plaid creates Item)

 3    POST /exchange-token           itemPublicTokenExchange()         plaid_items: INSERT (ACTIVE)
                                     itemGet()                         plaid_accounts: INSERT × N
                                     institutionsGetById()             Kafka: BankAccountLinkedEvent
                                     accountsGet()

 4    GET  /accounts                 (none — reads DB)                 (none)

 5    DELETE /accounts/{id}          itemRemove() if last account      plaid_accounts: active=false
                                                                        plaid_items: status=REMOVED
                                                                        (if last account)

 6    POST /payments/ach             transferAuthorizationCreate()     ach_payments: INSERT (PENDING)
                                     transferCreate()                   Kafka: AchPaymentInitiatedEvent

 7    POST /webhooks/plaid           transferEventSync()               ach_payments: status=POSTED
      (TRANSFER_EVENTS_PENDING)                                                    or FAILED
                                                                                   or CANCELLED

 8    POST /webhooks/plaid           (none)                            plaid_items: status=ERROR
      (ITEM/ERROR)
```

---

## What Lives Where

```
Plaid's side (their servers):
  Item         → the bank connection (Alex ↔ Chase). Has the access_token validity.
  Transfer     → the ACH debit record with lifecycle: pending → posted/failed/cancelled
  Institution  → Chase, BoA, Wells Fargo, etc. (Plaid maintains this directory)

Your DB (payment-service):
  plaid_items    → mirrors Plaid's Item. Stores access_token + status.
  plaid_accounts → mirrors accounts under an Item. Stores plaid_account_id for API calls.
  ach_payments   → your record of each transfer. Status updated by webhooks.

Kafka (event bus):
  BankAccountLinkedEvent  → other services know Alex linked his bank
  AchPaymentInitiatedEvent → other services know a debit was started
  (status updates stay inside payment-service — no Kafka event per webhook yet)
```
