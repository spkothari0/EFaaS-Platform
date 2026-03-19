# EFaaS Platform - Embedded Finance-as-a-Service API

[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> A production-grade, enterprise-scale fintech platform built with Spring Boot, Kafka, PostgreSQL, and TigerGraph. This is a portfolio project demonstrating advanced microservices architecture, multi-tenancy, and fraud detection using knowledge graphs.

## 🎯 Overview

EFaaS (Embedded Finance-as-a-Service) is a platform that enables businesses to embed financial services (payments, lending, investments) into their own products via APIs. The architecture follows the patterns used by companies like Stripe, Plaid, and Unit to provide Banking-as-a-Service infrastructure.

### Key Features

- **Multi-Tenant Architecture**: Complete tenant isolation with API key authentication and per-tenant rate limiting
- **Event-Driven Microservices**: Services communicate via Apache Kafka for reliable, asynchronous workflows
- **Graph-Based Fraud Detection**: TigerGraph knowledge graph detects fraud rings and AML patterns in milliseconds
- **Real FinTech Integrations**: Stripe (payments), Plaid (banking), Alpaca (trading) in sandbox mode
- **Enterprise-Grade**: Production-ready with Docker, CI/CD, monitoring, and structured logging

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│         Tenant Business Clients (REST API)              │
└─────────┬───────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────┐
│   Spring Cloud Gateway (Port 8080)                      │
│   - API Key Auth                                        │
│   - Rate Limiting (Redis)                               │
│   - Tenant Context Injection                            │
└──┬────┬────┬────┬────┬──────────────────────────────────┘
   │    │    │    │    │
   ▼    ▼    ▼    ▼    ▼
┌──┴┐┌──┴┐┌──┴┐┌──┴┐┌──┴───┐
│TM ││PS ││LS ││IS ││Fraud │
│   ││   ││   ││   ││Svc   │
└───┘└───┘└───┘└───┘└───┬──┘
   │   │    │    │      │
   └────────┬─────────┬─┘
            │ Kafka   │
            ▼         ▼
        PostgreSQL   TigerGraph
        (5 dbs)      (Graph DB)
```

**Services:**
- **Gateway**: Request routing, authentication, rate limiting (port 8080)
- **Tenant Service**: Multi-tenant management, API keys (port 8081)
- **Payment Service**: Stripe + Plaid integration (port 8082)
- **Lending Service**: Credit scoring, loans (port 8083)
- **Investment Service**: Alpaca paper trading (port 8084)
- **Fraud Service**: TigerGraph-powered fraud detection (port 8085)

## 🛠️ Tech Stack

| Category | Technology | Purpose |
|----------|-----------|---------|
| **Language** | Java 17+ | Enterprise backend |
| **Framework** | Spring Boot 3.3.x | Microservices |
| **API Gateway** | Spring Cloud Gateway | Routing, auth |
| **Messaging** | Apache Kafka | Event streaming |
| **Databases** | PostgreSQL (5) + TigerGraph | Relational + Graph |
| **Cache** | Redis | Rate limiting, sessions |
| **External APIs** | Stripe, Plaid, Alpaca | Fintech services |
| **Docs** | SpringDoc OpenAPI | Auto-generated Swagger |
| **CI/CD** | GitHub Actions | Build & deploy |
| **Monitoring** | Prometheus + Grafana | Metrics & dashboards |
| **Containers** | Docker + Compose | Orchestration |

## 📋 Prerequisites

- **Java 17+**: [Install here](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
- **Maven 3.8+**: [Install here](https://maven.apache.org/download.cgi)
- **Docker & Docker Compose**: [Install here](https://docs.docker.com/get-docker/)
- **Git**: [Install here](https://git-scm.com/)

## 🚀 Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/efaas-platform.git
cd efaas-platform
```

### 2. Start Infrastructure (Docker Compose)
```bash
docker-compose up -d
```

This starts:
- 5 PostgreSQL instances (ports 5433-5437)
- Kafka + Zookeeper (port 9092)
- Redis (port 6379)
- TigerGraph (ports 14240, 9000)

Verify all services are healthy:
```bash
docker-compose ps
docker-compose logs -f  # Watch startup
```

### 3. Build Project
```bash
mvn clean install
```

### 4. Run Services
Each service can be run independently:

```bash
# Terminal 1: Gateway
cd gateway && mvn spring-boot:run

# Terminal 2: Tenant Service
cd tenant-service && mvn spring-boot:run
```

Services will be available at:
- **Gateway**: http://localhost:8080
- **Tenant Service**: http://localhost:8081
- **API Docs**: http://localhost:8081/swagger-ui.html

## 📚 API Examples

### Create Tenant
```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Corp",
    "email": "admin@acme.com",
    "plan": "BASIC"
  }'
```

### Generate API Key
```bash
curl -X POST http://localhost:8080/api/v1/tenants/{tenantId}/api-keys
```

Response contains:
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "key": "efaas_live_abcdef123456...",  // Store securely
  "maskedKey": "efaas_live_...a5c9b"
}
```

### Using API Key
```bash
curl -H "X-API-Key: efaas_live_abcdef123456..." \
  http://localhost:8080/api/v1/payments
```

## 📖 Documentation

- **[DEMO.md](DEMO.md)** - Walk-through of key features with curl commands
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Development guidelines
- **[Architecture Decision Records](docs/adr/)** - Design rationale (Week 2+)
- **Swagger UI**: http://localhost:8081/swagger-ui.html

## 🗂️ Project Structure

```
efaas-platform/
├── pom.xml                      # Parent Maven POM
├── docker-compose.yml           # Development environment
├── docker-compose.prod.yml      # Production environment
│
├── common/                      # Shared library
│   └── src/main/java/com/efaas/common/
│       ├── dto/                # Data Transfer Objects
│       ├── event/              # Domain Events (Kafka)
│       └── exception/           # Custom exceptions
│
├── gateway/                     # API Gateway
├── tenant-service/              # Tenant management
├── payment-service/             # Payment processing
├── lending-service/             # Lending & credit scoring
├── investment-service/          # Trading & portfolio
├── fraud-service/               # Fraud detection
│
├── prometheus/                  # Monitoring config
├── grafana/                     # Dashboards
└── docs/                        # Architecture docs
```

## 🔐 Security

- **API Key Authentication**: SHA-256 hashed keys stored in database
- **Multi-Tenancy**: Complete data isolation via tenantId
- **Rate Limiting**: Redis-backed per-tenant rate limits
- **JWT Ready**: Skeleton in place for token-based auth (Week 2)
- **Encrypted Secrets**: Environment variables for prod (no hardcoded passwords)

## 📊 Monitoring

Once services are running, view metrics:

```bash
# Prometheus (metrics collection)
http://localhost:9090

# Grafana (dashboards)
http://localhost:3000  # (add in Week 8)

# Health checks
curl http://localhost:8081/actuator/health
```

## 🧪 Testing

```bash
# Run all tests
mvn clean test

# Run integration tests
mvn clean verify

# Run specific service tests
mvn test -pl tenant-service
```

## 🔄 Development Workflow

1. **Feature branches**: `git checkout -b feature/your-feature`
2. **Test locally**: `mvn clean verify`
3. **Commit**: Follow conventional commits (feat:, fix:, docs:, etc.)
4. **Pull request**: Submit for code review
5. **CI/CD**: GitHub Actions validates build & tests
6. **Merge & Deploy**: Automatically deployed to staging/prod

## 📈 Build Timeline

| Week | Focus | Deliverables |
|------|-------|--------------|
| 1 | Skeleton | Maven structure, Docker, Tenant service ✅ |
| 2 | Gateway | Rate limiting, API key validation |
| 3 | Payments | Stripe integration, webhooks |
| 4 | Lending | Credit scoring, loan lifecycle |
| 5 | Investment | Alpaca trading, WebSocket |
| 6 | Fraud | TigerGraph schema, GSQL queries |
| 7 | Polish | Documentation, monitoring, tests |
| 8 | Deploy | CI/CD, production setup |

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## 📝 License

MIT License - see [LICENSE](LICENSE) file.

## 👨‍💻 Author

Built as a portfolio project to demonstrate enterprise fintech backend engineering.

**Contact**: [Your Name] - [Your Email/GitHub]

---

**Last Updated**: March 2024 | **Status**: Week 1 Complete ✅
