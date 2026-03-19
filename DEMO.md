# EFaaS Demo Walkthrough

This document will contain step-by-step demonstrations of all EFaaS platform features.

**Status**: Framework in progress (Week 1-7). Full demo walkthrough added in Week 8.

## Table of Contents

- [EFaaS Demo Walkthrough](#efaas-demo-walkthrough)
  - [Table of Contents](#table-of-contents)
  - [Tenant Management](#tenant-management)
    - [1. Create a Tenant](#1-create-a-tenant)
    - [2. Retrieve Tenant](#2-retrieve-tenant)
  - [API Key Generation](#api-key-generation)
    - [3. Generate API Key](#3-generate-api-key)
    - [4. List API Keys](#4-list-api-keys)
  - [Payment Processing](#payment-processing)
  - [Loan Application](#loan-application)
  - [Investment Trading](#investment-trading)
  - [Fraud Detection](#fraud-detection)
  - [End-to-End Flow](#end-to-end-flow)

## Tenant Management

### 1. Create a Tenant

```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Corporation",
    "email": "admin@acme.com",
    "plan": "BASIC"
  }'
```

**Expected Response** (201 Created):
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "name": "Acme Corporation",
  "email": "admin@acme.com",
  "plan": "BASIC",
  "active": true,
  "createdAt": "2024-03-19T10:15:30Z",
  "updatedAt": "2024-03-19T10:15:30Z"
}
```

Save the `id` for subsequent requests.

### 2. Retrieve Tenant

```bash
TENANT_ID="some-uid"

curl -X GET http://localhost:8080/api/v1/tenants/$TENANT_ID \
  -H "Content-Type: application/json"
```

---

## API Key Generation

### 3. Generate API Key

```bash
curl -X POST http://localhost:8080/api/v1/tenants/$TENANT_ID/api-keys
```

**Expected Response** (201 Created):
```json
{
  "id": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "tenantId": "some-uid",
  "key": "efaas_live_some-uid",  // Store securely!
  "maskedKey": "efaas_live_4Z8w...S0T1",
  "active": true,
  "createdAt": "2024-03-19T10:16:45Z"
}
```

**⚠️ Important**: Store the `key` value securely. It will not be shown again!

Save the `key` for API requests.

### 4. List API Keys

```bash
curl -X GET http://localhost:8080/api/v1/tenants/$TENANT_ID/api-keys
```

---

## Payment Processing

> **Note**: Available in Week 3. Features:
> - Create payment via Stripe PaymentIntents API
> - Receive Stripe webhooks for async confirmation
> - Kafka event publishing (payment.completed)
> - Lending service auto-matches repayments
> - Investment service triggers round-up auto-invest

---

## Loan Application

> **Note**: Available in Week 5. Features:
> - Credit scoring engine (5 weighted factors)
> - Loan application workflow
> - Amortization schedule generation
> - Automatic payment matching via Kafka
> - Overdue detection

---

## Investment Trading

> **Note**: Available in Week 6. Features:
> - Alpaca paper trading orders (market/limit/stop)
> - Portfolio tracking
> - Real-time WebSocket market data
> - Round-up auto-invest from payments

---

## Fraud Detection

> **Note**: Available in Week 7. Features:
> - TigerGraph knowledge graph
> - Fraud ring detection (3-hop traversal)
> - Velocity checks
> - Shared infrastructure clustering
> - Risk scoring API

---

## End-to-End Flow

> **Full walkthrough coming Week 8**
>
> This will demonstrate:
> 1. Create tenant
> 2. Generate API key
> 3. Link bank account (Plaid)
> 4. Make payment (Stripe)
> 5. Apply for loan (auto-matched repayment)
> 6. Place trade (auto-invest round-up)
> 7. Check fraud score
> 8. View Grafana metrics

---

**Last Updated**: March 2024 | **Next**: Week 2 (Gateway Hardening)
