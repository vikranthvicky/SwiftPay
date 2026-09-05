# SwiftPay — Real-Time Payment Ledger

SwiftPay is a real-time payment processing and ledger platform built with Spring Boot. It provides authenticated payment APIs, wallet management, transaction idempotency, asynchronous event processing with Kafka, Redis-based caching, PostgreSQL persistence, Docker deployment, Kubernetes manifests, automated CI, and performance testing.

---

## Architecture

```text
Client
  |
  v
Spring Boot Payment API
  |
  +----------------------+
  |                      |
  v                      v
Redis                PostgreSQL
Idempotency           Payment
24 hours              PENDING
  |                      |
  +----------+-----------+
             |
             v
          Kafka
   payment.initiated
             |
             v
        Ledger Service
             |
             v
     PostgreSQL Transaction
             |
       +-----+-----+
       |           |
       v           v
    Debit        Credit
    Sender       Receiver
       |           |
       +-----+-----+
             |
             v
       Payment Status
       COMPLETED / FAILED
             |
       +-----+-----+
       |           |
       v           v
payment.completed  payment.failed
```

---

## Technology Stack

- **Java 21**
- **Spring Boot 3.5.16**
- **Spring Web**
- **Spring Data JPA**
- **PostgreSQL 17**
- **Apache Kafka 4.0**
- **Redis 7**
- **Spring Security**
- **JWT**
- **Bean Validation**
- **Swagger / OpenAPI**
- **Docker / Docker Compose**
- **Kubernetes**
- **GitHub Actions**
- **k6**
- **tcpdump / Wireshark-compatible PCAP capture**

---

## Core Features

### 1. Payment Processing

Payments are created through:

```text
POST /v1/payments
```

A payment initially enters the `PENDING` state and is processed asynchronously through Kafka.

The ledger service performs the final balance validation and atomic debit/credit operation.

---

### 2. Transaction Idempotency

Redis provides transaction-level idempotency using the transaction ID.

Each transaction is reserved using an atomic Redis operation:

```text
SETNX / setIfAbsent
```

The idempotency key is retained for:

```text
24 hours
```

This prevents duplicate transaction processing.

Duplicate requests with the same transaction ID and the same payload return the existing transaction.

A duplicate transaction ID with a different payload is rejected.

---

### 3. Atomic Wallet Transfer

The ledger processes sender and receiver wallets inside a PostgreSQL transaction.

Wallet rows are locked using pessimistic database locking.

Wallets are always locked in deterministic UUID order to reduce the possibility of deadlocks during concurrent payments.

The transfer consists of:

```text
Sender balance -= amount
Receiver balance += amount
Payment status = COMPLETED
```

If the payment cannot be completed, the transaction is marked:

```text
FAILED
```

---

### 4. Insufficient Funds Protection

Before debiting the sender, the ledger performs a final balance check after acquiring the wallet database lock.

Example:

```text
Sender balance: ₹100
Payment amount: ₹500

Result:
FAILED
Reason:
Insufficient funds
```

The sender and receiver balances remain unchanged.

---

### 5. Currency Validation

Both sender and receiver wallet currencies are validated against the payment currency.

For example:

```text
Sender Wallet:   INR
Receiver Wallet: INR
Payment:         INR

Result:
Valid
```

A currency mismatch causes the payment to fail.

---

### 6. Kafka Event Processing

Kafka is used to decouple payment creation from ledger processing.

The application uses the following topics:

```text
payment.initiated
payment.completed
payment.failed
```

Payment flow:

```text
POST /v1/payments
        |
        v
Payment saved as PENDING
        |
        v
payment.initiated
        |
        v
Ledger Consumer
        |
        +--------------------+
        |                    |
        v                    v
   Successful            Failure
        |                    |
        v                    v
payment.completed      payment.failed
```

---

### 7. Kafka Consumer Retry

Kafka listener processing uses a retry mechanism with a fixed backoff.

The current configuration retries failed message processing before the message is considered failed.

Configured retry behavior:

```text
Backoff: 1 second
Retries: 3
```

---

### 8. Duplicate Kafka Message Protection

The ledger only processes payments in the:

```text
PENDING
```

state.

`COMPLETED` and `FAILED` are treated as terminal states.

Therefore, if Kafka delivers the same event more than once, an already completed or failed payment is not processed again.

---

### 9. Redis Wallet Balance Cache

Redis is also used for short-lived wallet balance caching.

Balance cache TTL:

```text
5 minutes
```

The cache is refreshed when wallet balances are updated by the ledger.

---

# API

## Health Check

```http
GET /health
```

---

## Authentication

```http
POST /api/auth/login
```

Returns a JWT access token.

Use the token as:

```text
Authorization: Bearer <JWT>
```

---

## User Registration

```http
POST /api/users
```

Example request:

```json
{
  "email": "user@example.com",
  "password": "password"
}
```

---

## Create Wallet

```http
POST /api/wallets/{userId}?initialBalance=1000.00
```

Example:

```text
POST /api/wallets/f311af7d-e59c-453a-b50f-35c694636ea2?initialBalance=1000.00
```

The `initialBalance` parameter is provided for deterministic development and hackathon demonstration.

Production systems should use a controlled wallet funding workflow.

---

## Get Wallet

```http
GET /api/wallets/user/{userId}
```

Example:

```text
GET /api/wallets/user/f311af7d-e59c-453a-b50f-35c694636ea2
```

---

## Create Payment

```http
POST /v1/payments
```

Example:

```json
{
  "transaction_id": "22222222-2222-2222-2222-222222222222",
  "sender_id": "<sender-uuid>",
  "receiver_id": "<receiver-uuid>",
  "amount": 100.00,
  "currency": "INR"
}
```

Depending on processing state, the payment is initially stored as `PENDING` and is subsequently processed by the Kafka ledger consumer.

---

## Transaction History

```http
GET /v1/payments/history/{userId}
```

Returns payment history for the specified user.

---

# Payment Statuses

Payments can have the following statuses:

```text
PENDING
COMPLETED
FAILED
```

### PENDING

Payment has been accepted and is waiting for asynchronous ledger processing.

### COMPLETED

The sender was debited and the receiver was credited successfully.

### FAILED

The payment could not be completed.

Possible failure reasons include:

- Insufficient funds
- Currency mismatch
- Missing wallet
- Other payment processing errors

---

# Swagger / OpenAPI

After starting the application, Swagger UI is available at:

```text
http://localhost:8081/swagger-ui/index.html
```

OpenAPI specification:

```text
http://localhost:8081/v3/api-docs
```

Swagger can be used to:

1. Register users.
2. Login.
3. Copy the JWT token.
4. Authorize the Swagger session.
5. Create wallets.
6. Submit payments.
7. View transaction history.

---

# Running the Application

## Prerequisites

Install:

- Java 21
- Maven
- Docker Desktop

For performance testing:

- Docker
- k6, or the `grafana/k6` Docker image

---

# Docker Compose

The complete SwiftPay environment can be started using:

```powershell
docker compose up -d --build
```

The environment contains:

```text
SwiftPay Application
PostgreSQL
Kafka
Redis
```

Check running services:

```powershell
docker compose ps
```

Expected services:

```text
swiftpay-app
swiftpay-postgres
swiftpay-kafka
swiftpay-redis
```

---

## Application Logs

View application logs:

```powershell
docker compose logs -f app
```

View Kafka logs:

```powershell
docker compose logs -f kafka
```

---

# Local Development

For IntelliJ IDEA or another local Java development environment, the application can use:

```text
DB_URL=jdbc:postgresql://localhost:5432/swiftpay
DB_USERNAME=swiftpay
DB_PASSWORD=swiftpay123
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
REDIS_HOST=localhost
REDIS_PORT=6379
```

The application runs on:

```text
http://localhost:8081
```

---

# End-to-End Demo

## 1. Start Infrastructure

```powershell
docker compose up -d --build
```

Check:

```powershell
docker compose ps
```

---

## 2. Open Swagger

Open:

```text
http://localhost:8081/swagger-ui/index.html
```

---

## 3. Register Two Users

Use:

```http
POST /api/users
```

Create a sender and receiver.

---

## 4. Login

Use:

```http
POST /api/auth/login
```

Save the returned JWT.

Authorize Swagger using:

```text
Bearer <JWT>
```

---

## 5. Create Wallets

For a successful payment demonstration:

```text
POST /api/wallets/{senderId}?initialBalance=1000.00
```

and:

```text
POST /api/wallets/{receiverId}?initialBalance=0.00
```

---

## 6. Submit Payment

Example:

```json
{
  "transaction_id": "22222222-2222-2222-2222-222222222222",
  "sender_id": "<sender-uuid>",
  "receiver_id": "<receiver-uuid>",
  "amount": 100.00,
  "currency": "INR"
}
```

---

## 7. Verify Payment

Use:

```http
GET /v1/payments/history/{userId}
```

The payment should transition from:

```text
PENDING
```

to:

```text
COMPLETED
```

or:

```text
FAILED
```

depending on the ledger processing result.

---

# Failure Scenarios

The application supports demonstration of the following failure scenarios:

### Insufficient Funds

```text
Wallet balance < payment amount
```

Result:

```text
FAILED
```

with:

```text
Insufficient funds
```

---

### Duplicate Transaction

Submitting the same transaction ID with the same payment data returns the existing transaction.

---

### Duplicate Transaction With Different Payload

Submitting the same transaction ID with different payment details is rejected with:

```text
409 CONFLICT
```

---

### Duplicate Kafka Delivery

A payment that has already reached a terminal state is not processed again.

---

### Currency Mismatch

If the wallet currency does not match the payment currency, the payment fails.

---

# Testing

Run all Maven tests:

```powershell
mvn clean test
```

Current unit-test coverage includes payment idempotency behavior and ledger insufficient-funds behavior.

Tests are located under:

```text
src/test/java/com/swiftpay/service/
```

Current test classes include:

```text
PaymentServiceTest
LedgerServiceTest
```

---

# Performance Testing

The hackathon performance target is:

```text
250 transactions/second
1,000,000 transactions
```

At a sustained 250 TPS:

```text
1,000,000 / 250
= 4,000 seconds
= 66 minutes 40 seconds
```

The performance test is implemented using k6.

The test script is located at:

```text
performance/k6/payment-load-test.js
```

---

## k6 Docker Runner

If k6 is not installed locally, verify the Docker image:

```powershell
docker run --rm -i grafana/k6 version
```

Example output:

```text
k6 v2.2.0
```

---

## Short Validation Run

Before a long performance run, execute a short validation test.

```powershell
docker run --rm -i `
  --network swiftpay_default `
  -e BASE_URL=http://swiftpay-app:8081 `
  -e SENDER_ID=<sender-uuid> `
  -e RECEIVER_ID=<receiver-uuid> `
  -e AMOUNT=1.00 `
  -e DURATION=30s `
  -e RATE=10 `
  -e TOKEN="<jwt-token>" `
  grafana/k6 run - < .\performance\k6\payment-load-test.js
```

---

## 250 TPS Performance Run

The full target duration is:

```text
4,000 seconds
```

Example configuration:

```powershell
docker run --rm -i `
  --network swiftpay_default `
  -e BASE_URL=http://swiftpay-app:8081 `
  -e SENDER_ID=<sender-uuid> `
  -e RECEIVER_ID=<receiver-uuid> `
  -e AMOUNT=1.00 `
  -e DURATION=4000s `
  -e RATE=250 `
  -e PRE_ALLOCATED_VUS=500 `
  -e MAX_VUS=1000 `
  -e TOKEN="<jwt-token>" `
  grafana/k6 run - < .\performance\k6\payment-load-test.js
```

---

# Observed Performance Validation

A full-duration performance run was executed against the Docker-based SwiftPay environment.

Observed results:

```text
Duration:              1h 6m 37.6s
Target rate:           250 TPS
Completed iterations:  777,515
Dropped iterations:    222,552
Maximum VUs:            500
```

The run exercised the application at the configured **250 TPS target** for approximately the required 66-minute duration.

However, k6 reported dropped iterations because the available VUs were insufficient to complete every scheduled iteration.

Therefore, this run is recorded as a **250 TPS performance validation run** and is **not represented as proof of 1,000,000 successfully submitted transactions**.

A future final validation run should achieve:

```text
iterations = 1,000,000
dropped_iterations = 0
```

---

# PCAP Capture

Network traffic was captured during the performance test using `tcpdump`.

The capture was performed from the SwiftPay application container's network namespace.

The PCAP artifact is stored at:

```text
performance/pcap/swiftpay-payment-load.pcap
```

Observed artifact:

```text
File size:          6,900,419 bytes
Captured packets:  51,255
```

The PCAP was generated from the actual Docker-based performance test.

---

## Starting PCAP Capture

The following command can be used to capture application-container network traffic:

```powershell
docker run --rm `
  --name swiftpay-pcap `
  --network container:swiftpay-app `
  --cap-add NET_ADMIN `
  --cap-add NET_RAW `
  -v "${PWD}\performance\pcap:/captures" `
  nicolaka/netshoot `
  tcpdump -i any -s 0 -w /captures/swiftpay-payment-load.pcap
```

Keep the capture running while the performance test executes.

Stop the capture with:

```text
Ctrl+C
```

---

## PCAP Verification

Check the PCAP file:

```powershell
Get-Item .\performance\pcap\swiftpay-payment-load.pcap |
Select-Object Name, Length
```

Count captured packets:

```powershell
docker run --rm `
  -v "${PWD}\performance\pcap:/captures" `
  nicolaka/netshoot `
  sh -c "tcpdump -nn -r /captures/swiftpay-payment-load.pcap 2>/dev/null | wc -l"
```

The current validation PCAP contains:

```text
51,255 packets
```

The PCAP can also be opened in Wireshark for detailed inspection.

---

# Performance Test Architecture

During the performance test:

```text
                 +----------------+
                 |      k6        |
                 | 250 TPS target |
                 +-------+--------+
                         |
                         v
                 +----------------+
                 | SwiftPay App   |
                 | Spring Boot    |
                 +-------+--------+
                         |
             +-----------+-----------+
             |           |           |
             v           v           v
          Redis       Kafka       PostgreSQL
             |           |
             |           v
             |        Ledger
             |           |
             +-----------+
```

The PCAP captures network traffic generated during this environment.

---

# Kubernetes

Kubernetes manifests are provided under:

```text
k8s/
```

Files include:

```text
namespace.yaml
configmap.yaml
secret.yaml
postgres.yaml
redis.yaml
kafka.yaml
app-deployment.yaml
kustomization.yaml
```

---

## Build Application Image

```powershell
mvn clean package -DskipTests
```

Build Docker image:

```powershell
docker build -t swiftpay:latest .
```

---

## Apply Kubernetes Configuration

```powershell
kubectl apply -k k8s/
```

Check the namespace:

```powershell
kubectl get pods -n swiftpay
```

Check services:

```powershell
kubectl get services -n swiftpay
```

---

# CI/CD

GitHub Actions configuration is located at:

```text
.github/workflows/ci.yml
```

The CI pipeline performs:

1. Repository checkout
2. Java 21 setup
3. Maven dependency caching
4. Maven tests
5. Application packaging
6. Docker image build

The workflow runs for:

```text
push -> main
pull_request -> main
```

---

# Project Structure

```text
SwiftPay/
│
├── .github/
│   └── workflows/
│       └── ci.yml
│
├── k8s/
│   ├── app-deployment.yaml
│   ├── configmap.yaml
│   ├── kafka.yaml
│   ├── kustomization.yaml
│   ├── namespace.yaml
│   ├── postgres.yaml
│   ├── redis.yaml
│   └── secret.yaml
│
├── performance/
│   ├── k6/
│   │   └── payment-load-test.js
│   ├── pcap/
│   │   └── swiftpay-payment-load.pcap
│   └── README.md
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── swiftpay/
│   │   │           ├── config/
│   │   │           ├── controller/
│   │   │           ├── dto/
│   │   │           ├── entity/
│   │   │           ├── event/
│   │   │           ├── exception/
│   │   │           ├── kafka/
│   │   │           ├── repository/
│   │   │           └── service/
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── swiftpay/
│                   └── service/
│
├── .dockerignore
├── .gitignore
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

---

# Configuration

The application supports environment-variable overrides.

Database:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
DB_POOL_MAX_SIZE
DB_POOL_MIN_IDLE
```

Kafka:

```text
KAFKA_BOOTSTRAP_SERVERS
```

Redis:

```text
REDIS_HOST
REDIS_PORT
```

JWT:

```text
JWT_SECRET
JWT_EXPIRATION_MS
```

---

# Security

Development/demo credentials are currently provided through Docker Compose configuration.

For production deployments:

- Use externally managed secrets.
- Use a strong environment-specific JWT secret.
- Enable TLS.
- Do not commit credentials or tokens.
- Use production database credentials.
- Use appropriate Kafka security configuration.
- Restrict access to infrastructure services.

Never commit real JWT tokens, passwords, API keys, or other secrets to the repository.

---

# Important Performance Note

The current PCAP and performance results represent an actual validation run.

The observed k6 result was:

```text
250 TPS target
777,515 completed iterations
222,552 dropped iterations
```

The current PCAP contains:

```text
51,255 packets
```

The full hackathon target requires a successful run demonstrating:

```text
250 TPS
1,000,000 transactions
0 dropped iterations
```

The existing performance artifact is therefore retained as validation evidence, while the exact 1M/250 TPS requirement remains a target for final validation.

---

# Development Commands

Run tests:

```powershell
mvn clean test
```

Build application:

```powershell
mvn clean package -DskipTests
```

Start Docker environment:

```powershell
docker compose up -d --build
```

Check containers:

```powershell
docker compose ps
```

Stop environment:

```powershell
docker compose down
```

View application logs:

```powershell
docker compose logs -f app
```

---

# License

This project was developed as part of a hackathon submission.