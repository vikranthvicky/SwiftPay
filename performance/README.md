# SwiftPay Performance Test

The hackathon target is 250 transactions/second and 1,000,000 transactions.

## Short validation run

```bash
k6 run -e BASE_URL=http://localhost:8081 -e SENDER_ID=<sender-uuid> -e RECEIVER_ID=<receiver-uuid> -e RATE=250 -e DURATION=60s performance/k6/payment-load-test.js
```

## 1M transaction run

At 250 TPS, 1,000,000 transactions requires 4,000 seconds (66 minutes 40 seconds), assuming the target rate is sustained.

```bash
k6 run -e BASE_URL=http://localhost:8081 -e SENDER_ID=<sender-uuid> -e RECEIVER_ID=<receiver-uuid> -e RATE=250 -e DURATION=4000s -e PRE_ALLOCATED_VUS=500 -e MAX_VUS=1000 performance/k6/payment-load-test.js
```

Do not run the 1M test until the 60-second smoke test is successful.
