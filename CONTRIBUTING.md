# Contributing to EFaaS Platform

Thank you for your interest in contributing to the EFaaS platform! This document provides guidelines for development.

## Getting Started

1. **Fork** the repository
2. **Clone** your fork locally
3. **Create** a feature branch: `git checkout -b feature/your-feature-name`
4. **Develop** following our standards (see below)
5. **Test** thoroughly: `mvn clean verify`
6. **Commit** with clear messages
7. **Push** to your fork
8. **Submit** a pull request

## Development Standards

### Code Style

- **Java Conventions**: Follow standard Java naming and formatting
- **Formatting**: Use 4 spaces for indentation
- **Imports**: Organize imports alphabetically
- **Line Length**: Max 120 characters
- **Lombok**: Use for reducing boilerplate (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`)
- **Comments**: Add comments for non-obvious logic; avoid over-commenting

### Architecture Principles

- **Single Responsibility**: Each class has one reason to change
- **Dependency Injection**: Use Spring's `@Component`, `@Service`, `@Repository`
- **Immutability**: Prefer immutable objects (especially for domain events)
- **No Magic**: Explicit over implicit; avoid hidden behavior
- **Clean Layers**: DTOs in API, Entities in persistence, Services for logic
- **Multi-Tenancy**: Always include `tenantId` in domain objects where relevant

### Package Structure

```
com.efaas.{service}/
├── domain/          # JPA entities
├── repository/      # Data access
├── service/         # Business logic
├── controller/      # HTTP endpoints
├── exception/       # Custom exceptions
└── {Service}Application.java
```

### Exception Handling

- **Extend** `EFaaSException` for business logic errors
- **Use** custom exceptions (e.g., `TenantNotFoundException`)
- **Return** RFC 7807 `ProblemDetail` responses from `GlobalExceptionHandler`
- **Never** expose stack traces in API responses

```java
// ❌ Bad
throw new RuntimeException("Tenant not found");

// ✅ Good
throw new TenantNotFoundException(tenantId.toString());
```

### Testing

- **Unit Tests**: Suffix `Tests.java` (e.g., `TenantServiceTests.java`)
- **Integration Tests**: Suffix `IntegrationTests.java` and use `@Testcontainers`
- **Coverage**: Aim for 80%+ code coverage on services
- **Testcontainers**: Use for realistic database/Kafka testing

```java
@SpringBootTest
@Testcontainers
class TenantServiceIntegrationTests {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(...);
    // ...
}
```

### Database

- **Migrations**: Use Flyway in production (Week 8+)
- **Naming**: Use snake_case for table/column names
- **Indexes**: Index foreign keys and frequently queried columns
- **DTOs**: Always convert entities to DTOs before returning from API

### Kafka Events

- **Events**: Extend `DomainEvent` base class
- **Topics**: Naming convention: `{service}.{event-type}` (e.g., `payment.completed`)
- **Serialization**: Use JSON with `@JsonTypeInfo` for polymorphism
- **Idempotency**: Design consumers to be idempotent

```java
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
public class PaymentCompletedEvent extends DomainEvent {
    private UUID paymentId;
    // ...
}
```

### API Documentation

- **OpenAPI**: Use `@Operation`, `@ApiResponse`, `@Schema` from SpringDoc
- **Request/Response**: Document with examples
- **Errors**: Document possible error responses (e.g., 404, 401, 400)

```java
@PostMapping
@Operation(summary = "Create tenant")
@ApiResponse(responseCode = "201", description = "Tenant created")
public ResponseEntity<TenantDTO> create(@Valid @RequestBody CreateRequest req) {
    // ...
}
```

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(tenant): add API key rotation endpoint
fix(payment): handle Stripe webhook retries correctly
docs(README): update quick start instructions
refactor(gateway): extract authentication logic
test(lending): add credit scoring edge cases
chore(deps): upgrade Spring Boot to 3.3.1
```

**Format**: `<type>(<scope>): <description>`

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `refactor`: Code restructuring
- `test`: Tests
- `chore`: Build, deps, tooling
- `style`: Formatting (no logic change)

### Pull Request Process

1. **Title**: Use conventional commit format
2. **Description**: Explain "what" and "why"
3. **Testing**: List testing steps
4. **Links**: Reference related issues/PRs
5. **Reviews**: Need at least 1 approval before merge

**Example PR Description**:
```markdown
## Summary
Adds API key rotation endpoint for tenants to enhance security.

## Changes
- New endpoint: POST /api/v1/tenants/{id}/api-keys/rotate
- Deactivates old key, generates new one atomically
- Publishes `api_key.rotated` event

## Testing
- [x] Unit tests for ApiKeyService
- [x] Integration test with real database
- [x] Manual test: `curl -X POST http://localhost:8081/...`

## Related Issues
Closes #123
```

## Performance Considerations

- **Connection Pooling**: HikariCP configured (10 max connections per service)
- **Batch Operations**: Use Hibernate batch size = 20
- **Caching**: Redis for rate limit counters, session data
- **Async**: Use Kafka for cross-service communication (not REST)
- **Indexes**: Always index foreign keys

## Security

- **Secrets**: Never commit credentials, use environment variables
- **Hashing**: API keys SHA-256 hashed, never stored plaintext
- **Validation**: Validate all inputs with `@Valid` annotations
- **SQL Injection**: Use parameterized queries (JPA/Hibernate)
- **CORS**: Configured per environment

## Running Locally

```bash
# Build
mvn clean install

# Start infrastructure
docker-compose up -d

# Run services
cd gateway && mvn spring-boot:run &
cd tenant-service && mvn spring-boot:run &

# Run tests
mvn clean verify

# View logs
docker-compose logs -f

# Stop
docker-compose down
```

## Questions?

- Check existing issues/PRs first
- Review [README.md](README.md) and [DEMO.md](DEMO.md)
- Ask in PR comments or create a discussion

---

**Code Review Guidelines**: We'll evaluate for:
- Correctness
- Clean code and architecture
- Test coverage
- Documentation
- Security (no hardcoded secrets, input validation)

Thank you for contributing! 🙏
