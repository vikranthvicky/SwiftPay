# SwiftPay — AI Prompts

## Purpose

This document records representative prompts used during the AI-assisted development and debugging workflow for SwiftPay.

The prompts below are reconstructed from the development activities and technical problems encountered during the project. They are intended to document the AI-native engineering process and are not presented as a verbatim transcript of every historical AI interaction.

---

# 1. Architecture

### Prompt

> I am building SwiftPay, a real-time P2P payment ledger using Java/Spring Boot, PostgreSQL, Kafka, Redis, Docker and Swagger/OpenAPI. The payment API should persist a PENDING payment, use Redis for idempotency, publish a PaymentInitiated Kafka event, and have a ledger consumer process the payment and publish completion/failure events. Propose a clean backend architecture with controllers, DTOs, services, repositories and Kafka components. Keep the design simple enough for a hackathon while preserving transaction consistency and idempotency.

---

# 2. Payment API

### Prompt

> Design the Spring Boot POST `/v1/payments` implementation for SwiftPay. The request contains transactionId, senderId, receiverId, amount and currency. Validate the request, persist the initial payment as PENDING, enforce idempotency using Redis, and publish a PaymentInitiated Kafka event. Explain the controller-to-service-to-repository flow and where transaction boundaries should be considered.

---

# 3. Redis Idempotency

### Prompt

> Review this Redis idempotency implementation for a payment API. The requirement is that the same transactionId must not be processed twice. Identify possible race conditions, incorrect TTL handling and failure scenarios. Suggest a minimal implementation that is safe under concurrent payment requests.

---

# 4. Kafka Event Design

### Prompt

> Review the SwiftPay PaymentInitiated, PaymentCompleted and PaymentFailed event classes together with the Kafka producer and consumer configuration. Check JSON serialization/deserialization, topic names, consumer group configuration and event compatibility. Identify potential problems and suggest the smallest safe correction.

---

# 5. Kafka Deserialization Error

### Prompt

> My Spring Kafka consumer is reporting a deserialization/SerializationException involving JsonDeserializer. Here is the exact Kafka error, event class and KafkaConfig. Determine the likely cause, explain the expected key/value types and show how the consumer should be configured. Include validation steps after the change.

---

# 6. Kafka Consumer Troubleshooting

### Prompt

> These are the Kafka and Spring Boot logs from my SwiftPay application. Analyze the consumer-group joins, partition assignments, coordinator changes and errors. Separate normal Kafka lifecycle messages from actionable failures and tell me which component or configuration I should inspect next.

---

# 7. JWT / 401 Troubleshooting

### Prompt

> My POST `/v1/payments` request returns HTTP 401 even though login returns an accessToken. Here are the login response, Authorization header construction and Spring Security/JWT configuration. Identify why the token may not be accepted and give me PowerShell commands to verify the authentication flow without exposing secrets.

---

# 8. HTTP 500 Troubleshooting

### Prompt

> My authenticated POST `/v1/payments` request returns HTTP 500 with `INTERNAL_SERVER_ERROR`. Here is the exact response and application log output around the request. Trace the likely failure path through the controller, service, database and Kafka layers. Do not assume a root cause if the logs do not support it. Tell me exactly what additional evidence is needed.

---

# 9. K6 Load-Test Design

### Prompt

> Create a K6 constant-arrival-rate test for SwiftPay POST `/v1/payments`. Make the target rate, duration, base URL, sender ID, receiver ID, amount and JWT token configurable through environment variables. Generate a unique transactionId for every iteration and verify that the API returns HTTP 201.

---

# 10. K6 Runtime Error

### Prompt

> K6 is failing before making HTTP requests with `TypeError: Value is not an object: undefined` at the transactionId generation line. Here is the complete script and exact line number. Identify the problem with the K6 runtime/import and provide a compatible replacement. Then give me a minimal test command.

---

# 11. K6 Performance Analysis

### Prompt

> Analyze these K6 results for the SwiftPay payment endpoint. Explain request rate, latency, p90, p95, failed requests, dropped iterations, completed iterations and VU usage. Determine whether the configured arrival rate was actually achieved. Separate application performance from load-generator limitations.

---

# 12. Docker Compose Troubleshooting

### Prompt

> My Docker containers are named with the SwiftPay project prefix, but `docker compose logs swiftpay-app` fails. `docker compose config --services` shows services named `postgres`, `redis`, `kafka` and `app`. Explain the difference between Docker container names and Compose service names and give me the correct log commands.

---

# 13. Docker Resource Analysis

### Prompt

> Here is `docker stats --no-stream` while the SwiftPay load test is running. Analyze CPU, memory, network I/O and process usage for the application, Kafka, PostgreSQL and Redis containers. Identify which components appear to be under the most load and explain what additional measurements would be required before concluding that a component is the bottleneck.

---

# 14. PCAP Capture

### Prompt

> I need a PCAP trace for SwiftPay HTTP payment traffic generated during performance testing. Give me a Docker-based packet-capture approach using tshark/netshoot. The capture should be saved under `performance/pcap` and should allow me to inspect HTTP requests to `/v1/payments`.

---

# 15. PCAP Validation

### Prompt

> I have a SwiftPay PCAP file. Give me TShark commands to verify whether it contains HTTP requests and specifically `POST /v1/payments`. Also show how to count matching requests and inspect the first and last request timestamps.

---

# 16. PCAP Filter Troubleshooting

### Prompt

> My TShark display filter produces a syntax error when I use `http.request.uri == /v1/payments`. Explain the correct syntax for matching this HTTP URI and provide a PowerShell-compatible Docker command.

---

# 17. Performance Evidence Review

### Prompt

> Here are the K6 results, Docker resource statistics and PCAP validation output for SwiftPay. Summarize exactly what the evidence demonstrates. Separate successful API responses, achieved throughput, dropped iterations, latency and captured HTTP traffic. Do not claim that a 250 TPS or 1-million-transaction requirement was completed unless the evidence proves it.

---

# 18. Code Review

### Prompt

> Review this SwiftPay backend code as a senior backend engineer. Check correctness, security, concurrency, Kafka behavior, Redis idempotency, PostgreSQL transaction handling, API compatibility, error handling, observability and maintainability. Identify the highest-risk issues first and suggest practical fixes.

---

# 19. Security Review

### Prompt

> Review this SwiftPay API and configuration for common backend security issues including JWT handling, authorization, input validation, secret exposure, error responses, Redis usage and database access. Do not claim a vulnerability exists without evidence. For every concern, provide a concrete validation step.

---

# 20. Documentation

### Prompt

> Based only on the implemented SwiftPay source code and configuration, generate a concise README section explaining the payment request flow, PostgreSQL persistence, Redis idempotency, Kafka events, ledger processing, Docker services, health endpoint and performance testing. Do not document features that are not present in the implementation.

---

# 21. Root-Cause Analysis

### Prompt

> Analyze this SwiftPay backend incident using the following workflow: observed symptom → evidence → possible causes → most likely cause → relevant source/configuration → remediation → regression test. Clearly distinguish confirmed facts from hypotheses and tell me what evidence is still missing.

---

# 22. Kafka Log Analysis

### Prompt

> Analyze these Kafka logs from the SwiftPay ledger consumer. Explain which messages indicate normal consumer-group behavior and which indicate a possible problem. Pay particular attention to coordinator availability, heartbeat failures, rebalancing, partition assignment and consumer disconnects.

---

# 23. API Verification

### Prompt

> I have successfully logged into SwiftPay and received a JWT access token. Give me a PowerShell command to call POST `/v1/payments` using the token and a JSON body containing transactionId, senderId, receiverId, amount and currency. The command should print the HTTP status and response body for both success and failure.

---

# 24. Load-Test Troubleshooting

### Prompt

> My K6 test is configured for a higher arrival rate, but the results show many dropped iterations and the number of completed iterations is much lower than the configured rate. Explain what this means, how constant-arrival-rate works, how VUs affect it, and how to determine whether the limitation is K6 or the SwiftPay application.

---

# 25. AI-Assisted Engineering Review

### Prompt

> Review the proposed SwiftPay implementation as an AI-native backend engineering project. Evaluate whether the development workflow demonstrates meaningful AI usage across architecture, coding, debugging, performance testing and documentation. Identify claims that need stronger evidence before being included in the final submission.

---

# Prompting Principles Used

The prompts generally followed this pattern:

```text
Technical context
        +
Actual source/configuration
        +
Exact error or observed behavior
        +
Specific question
        +
Constraints
        +
Validation steps
```

This approach was used to avoid vague AI-generated solutions and to keep the development process evidence-driven.

---

# Human Validation

AI recommendations were validated using:

- Source-code inspection
- Application startup
- REST API requests
- PowerShell commands
- Docker logs
- Kafka logs
- PostgreSQL inspection
- Redis behavior
- K6 performance results
- TShark output
- PCAP inspection

AI output was therefore used as an accelerator for engineering work rather than as the sole source of technical decisions.