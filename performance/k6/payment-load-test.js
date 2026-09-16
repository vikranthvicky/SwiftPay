import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8081';
const SENDER_ID = __ENV.SENDER_ID;
const RECEIVER_ID = __ENV.RECEIVER_ID;
const AMOUNT = __ENV.AMOUNT || '1.00';
const DURATION = __ENV.DURATION || '10s';
const RATE = Number(__ENV.RATE || '250');
const TOKEN = __ENV.TOKEN;

export const options = {
  scenarios: {
    payments: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || '100'),
      maxVUs: Number(__ENV.MAX_VUS || '500'),
    },
  },
};

export default function () {
  const payload = JSON.stringify({
    transactionId: uuidv4(),
    senderId: SENDER_ID,
    receiverId: RECEIVER_ID,
    amount: AMOUNT,
    currency: 'INR',
  });

  const response = http.post(
    `${BASE_URL}/v1/payments`,
    payload,
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${TOKEN}`,
      },
    }
  );

  check(response, {
    'payment request accepted': (r) => r.status === 201,
  });
}