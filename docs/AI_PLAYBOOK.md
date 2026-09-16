# SwiftPay — AI-Native Backend Engineering Playbook

## 1. Purpose

This document describes how AI was incorporated into the development and engineering workflow for SwiftPay.

AI was used as an engineering copilot for architecture discussions, backend implementation, debugging, performance testing, network-trace validation, code review, and documentation.

AI-generated suggestions were treated as recommendations and were validated against the actual source code, application behavior, logs, test results, and infrastructure.

---

## 2. SwiftPay Technical Context

SwiftPay is a real-time payment backend built around:

- Java / Spring Boot
- PostgreSQL
- Apache Kafka
- Redis
- JWT authentication
- REST APIs
- Docker / Docker Compose
- Swagger / OpenAPI
- K6 performance testing
- PCAP / TShark network validation

The main payment flow is:

```text
Client
   |
   v
POST /v1/payments
   |
   v
Authentication / Authorization
   |
   v
Payment Service
   |
   +----> Redis Idempotency
   |
   +----> PostgreSQL Payment
   |
   v
Kafka: payment.initiated
   |
   v
Ledger Consumer
   |
   +----> Wallet / Ledger Processing
   |
   +----> payment.completed
   |
   +----> payment.failed
```

---

## 3. AI-Assisted Engineering Workflow

The development workflow followed:

```text
Requirement
    ↓
AI-assisted analysis
    ↓
Implementation
    ↓
Run application
    ↓
Observe logs / metrics / errors
    ↓
AI-assisted diagnosis
    ↓
Apply correction
    ↓
Run validation test
    ↓
Human review
```

AI was not treated as an automatic source of truth.

The generated code or recommendations were checked against the actual SwiftPay implementation before being retained.

---

## 4. Architecture and Design

AI was used to break the payment requirement into manageable backend components.

Areas discussed included:

- REST controller responsibilities
- DTO design
- Service/repository separation
- PostgreSQL persistence
- Redis idempotency
- Kafka event publishing
- Kafka event consumption
- Ledger processing
- JWT authentication
- API documentation
- Docker infrastructure
- Performance testing

The purpose was to keep the implementation modular while still being appropriate for a hackathon project.

---

## 5. Backend Implementation

AI assistance was used for reasoning about implementation patterns for:

### REST APIs

- Payment creation
- Authentication
- Wallet operations
- User operations
- Health checks

### Persistence

- PostgreSQL entities
- Repository patterns
- Payment persistence
- Wallet persistence
- User persistence

### Kafka

- Producer configuration
- Consumer configuration
- Event models
- Topic configuration
- Consumer groups
- JSON serialization/deserialization

### Redis

- Idempotency handling
- Transaction ID protection
- Duplicate request prevention

### Security

- JWT generation
- JWT validation
- Authentication filters
- Authorization
- Bearer token handling

### Error handling

- Authentication exceptions
- Conflict errors
- User-not-found errors
- Global exception handling
- API error responses

---

## 6. AI-Assisted Debugging

When an issue occurred, the debugging process was:

1. Capture the exact error.
2. Provide the relevant source code or configuration.
3. Ask AI to identify possible causes.
4. Separate confirmed facts from hypotheses.
5. Inspect the actual application behavior.
6. Apply the smallest appropriate correction.
7. Re-run the failing scenario.
8. Validate the result independently.

This approach was used for HTTP, Kafka, Docker, K6, authentication, and PCAP-related issues.

---

## 7. JWT / Authentication Debugging

During performance-test preparation, requests to:

```text
POST /v1/payments
```

returned `401 UNAUTHORIZED` in some runs.

The debugging process included:

- Checking the login endpoint.
- Inspecting the returned `accessToken`.
- Checking the token length.
- Inspecting the Authorization header.
- Comparing the manually executed PowerShell request with the K6 request.
- Re-running an authenticated payment request.

A manually verified request subsequently returned:

```text
HTTP 201
```

with a payment response containing:

```text
status: PENDING
```

This confirmed that the payment endpoint and authentication flow could successfully process an authenticated request.

---

## 8. K6 Performance Testing

K6 was used to generate payment requests against:

```text
POST /v1/payments
```

The load-test script uses environment variables for:

- `BASE_URL`
- `SENDER_ID`
- `RECEIVER_ID`
- `AMOUNT`
- `DURATION`
- `RATE`
- `TOKEN`
- `PRE_ALLOCATED_VUS`
- `MAX_VUS`

The test uses K6's:

```text
constant-arrival-rate
```

executor.

Each iteration creates a unique transaction ID.

The response is checked using:

```text
payment request accepted
```

where the expected HTTP status is:

```text
201
```

---

## 9. Performance-Test Debugging

The initial K6 test produced:

```text
TypeError: Value is not an object: undefined
```

The error pointed to the transaction ID generation line.

The script was investigated and corrected before continuing with HTTP performance testing.

Subsequent tests produced successful payment checks.

Example successful results observed during testing included:

```text
checks_succeeded: 100%
checks_failed:     0%
http_req_failed:    0%
```

Different target rates also demonstrated increasing latency and dropped iterations as the system/load-generator became more stressed.

These results were used to distinguish successful API responses from load-generator saturation.

---

## 10. Kafka Debugging

Kafka was investigated using both application logs and Kafka container logs.

The system uses topics including:

```text
payment.initiated
payment.completed
payment.failed
```

The ledger consumer uses the consumer group:

```text
swiftpay-ledger
```

Application logs were used to validate successful event processing.

Example observed processing:

```text
Received PaymentInitiated event transactionId=...
Ledger processing completed transactionId=...
```

Kafka logs were also inspected for:

- Consumer-group membership
- Partition assignment
- Rebalancing
- Coordinator changes
- Heartbeat problems
- Deserialization issues
- Fetch/disconnect errors

Normal Kafka lifecycle messages were separated from actual application failures before making changes.

---

## 11. Docker Troubleshooting

SwiftPay runs its infrastructure through Docker Compose.

The Compose service names are:

```text
postgres
redis
kafka
app
```

The actual container names may contain the SwiftPay project prefix.

Therefore, Compose commands should use service names, for example:

```powershell
docker compose logs --tail=100 app
docker compose logs --tail=100 kafka
docker compose logs --tail=100 postgres
docker compose logs --tail=100 redis
```

Docker resource usage was inspected with:

```powershell
docker stats --no-stream
```

This was used to observe CPU, memory, network I/O, block I/O, and process usage during performance testing.

---

## 12. PCAP / Network Validation

A PCAP trace was generated as part of the performance/network evidence.

The PCAP files were inspected using TShark through the Netshoot Docker image.

Example validation:

```powershell
docker run --rm `
  -v "${PWD}\performance\pcap:/captures" `
  nicolaka/netshoot `
  tshark -r /captures/swiftpay-payment-http-test.pcap `
  -Y 'http.request' `
  -T fields `
  -e http.request.method `
  -e http.request.uri
```

The resulting traffic contained requests such as:

```text
POST    /v1/payments
```

The payment PCAP was further inspected by filtering the request URI and counting matching requests.

---

## 13. PCAP Evidence

The payment HTTP PCAP was validated using TShark.

The observed capture contained thousands of:

```text
POST /v1/payments
```

requests.

The capture was also checked for the first and last relative timestamps to establish the duration of the captured traffic.

The repository contains the resulting performance/network artifacts under:

```text
performance/pcap/
```

---

## 14. AI-Assisted Code Review

AI was used to review backend code for:

- Correctness
- Authentication
- Authorization
- Input validation
- Idempotency
- Kafka behavior
- Database interactions
- Concurrency
- Error handling
- Performance
- Maintainability
- Observability

The objective was not simply to determine whether code compiled.

Generated recommendations were compared against the actual implementation and tested where applicable.

---

## 15. Security Review Approach

Security-sensitive recommendations were manually reviewed.

The review focused on:

- JWT handling
- Authorization
- Secrets
- Input validation
- Error responses
- Database access
- Redis usage
- API exposure
- Logging

Sensitive credentials and tokens were not intentionally included in repository documentation.

---

## 16. Prompting Strategy

Prompts generally followed this structure:

```text
Context
+
Observed evidence
+
Specific problem
+
Constraints
+
Expected outcome
+
Validation request
```

Example:

> I have a Spring Boot Kafka consumer and the following exact deserialization error. Here is my Kafka configuration and event class. Identify the likely root cause, explain why it occurs, suggest the smallest safe change, and provide commands to validate the fix.

This produced more useful results than asking AI to solve an issue without providing the actual evidence.

---

## 17. Human Validation

AI recommendations were validated using one or more of:

- Source-code inspection
- Compilation
- Application startup
- REST API calls
- PowerShell requests
- Docker logs
- Kafka logs
- PostgreSQL queries
- Redis behavior
- K6 results
- TShark / PCAP inspection

The final decision to retain a change remained with the developer.

---

## 18. Evidence-Based Engineering

Performance claims were only made when supported by actual test evidence.

For example:

```text
Successful API responses
```

are different from:

```text
Sustained target throughput
```

and both are different from:

```text
Completed 1 million transactions
```

K6 results, dropped iterations, request latency, VU usage, and PCAP contents were therefore considered separately.

This prevented performance conclusions from being based only on the configured K6 target rate.

---

## 19. AI Engineering Principles

The SwiftPay workflow followed these principles:

1. AI assists engineering; it does not replace engineering judgment.
2. Provide actual errors and evidence when asking for debugging help.
3. Prefer small and reviewable changes.
4. Validate generated code against runtime behavior.
5. Do not expose secrets in prompts or documentation.
6. Distinguish hypotheses from confirmed root causes.
7. Use logs and test results as evidence.
8. Re-test after changes.
9. Do not claim performance results that have not been demonstrated.
10. Keep the final implementation understandable and maintainable.

---

## 20. Summary

AI was integrated into the SwiftPay development lifecycle across:

```text
Architecture
    ↓
Backend implementation
    ↓
Authentication
    ↓
Kafka
    ↓
Redis
    ↓
PostgreSQL
    ↓
Docker
    ↓
K6 performance testing
    ↓
PCAP/TShark validation
    ↓
Debugging
    ↓
Code and security review
    ↓
Documentation
```

The goal was to demonstrate an AI-native backend engineering workflow where AI accelerates analysis and implementation while actual engineering decisions are validated through code, runtime behavior, tests, logs, and measurable evidence.