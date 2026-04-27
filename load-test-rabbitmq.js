import http from 'k6/http';
import crypto from 'k6/crypto';
import {check, sleep} from 'k6';

// 1. Configuration
export const options = {
    stages: [
        { duration: '30s', target: 300 },  // Ramp up to 300 concurrent users
        { duration: '2m', target: 300 },   // Hammer the 3 servers for 2 minutes
        { duration: '30s', target: 0 },    // Ramp down
    ],
    // Relax the duration threshold slightly, as 3 JVMs will fight for CPU locally
    thresholds: {
        'http_req_duration': ['p(95)<3000'],
    }
};

const SERVERS = [
    'http://localhost:9999/api/v1',
    'http://localhost:10000/api/v1',
    'http://localhost:10001/api/v1'
];

// const BASE_URL = 'http://localhost:80/api/v1';
const PAYSTACK_SECRET = 'sk_test_k6_load_testing_secret_key_override'; // Matches properties file

function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        let r = Math.random() * 16 | 0;
        let v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

// 2. Setup Phase (Runs once before the VUs start)
export function setup() {
    // Register Admin

    const BASE_URL = 'http://localhost:9999/api/v1';

    const adminPayload = JSON.stringify({
        email: "admin@quicktix.com",
        password: "Password123!"
    });

    let adminRes = http.post(`${BASE_URL}/auth/login`, adminPayload, {headers: {'Content-Type': 'application/json'}});
    const adminToken = `Bearer ${adminRes.json('token')}`;
    const adminHeaders = {'Authorization': adminToken, 'Content-Type': 'application/json'};

    // Create Venue
    const venuePayload = JSON.stringify({
        name: "K6 Load Testing Arena", address: "123 Stress Test Blvd", city: "Server City", totalCapacity: 100000
    });
    let venueRes = http.post(`${BASE_URL}/venues`, venuePayload, {headers: adminHeaders});
    const venueId = venueRes.json('id');

    // Create Massive Event
    const eventPayload = JSON.stringify({
        name: "Taylor Swift: The RabbitMQ Tour", description: "Stress testing the broker",
        venueId: venueId, eventStartDateTime: "2026-12-31T20:00:00Z", eventEndDateTime: "2026-12-31T23:00:00Z",
        ticketPrice: 150.00, numberOfSeats: 10000
    });
    let eventRes = http.post(`${BASE_URL}/events`, eventPayload, {headers: adminHeaders});

    // Pass the eventId to all Virtual Users
    return {eventId: eventRes.json('id')};
}

// 3. Virtual User Execution Phase
export default function (data) {
    if(!data.eventId) return;
    const eventId = data.eventId;
    const BASE_URL = SERVERS[Math.floor(Math.random() * SERVERS.length)];

    // Step A: Register a unique user for this iteration
    const userEmail = `user_${__VU}_${__ITER}_${Date.now()}@test.com`;
    const userPayload = JSON.stringify({
        firstName: "Load", lastName: "Tester", email: userEmail,
        password: "password123", phoneNumber: "0987654321", role: "USER"
    });

    let regRes = http.post(`${BASE_URL}/auth/register`, userPayload, {headers: {'Content-Type': 'application/json'}});
    if (regRes.status !== 200 && regRes.status !== 201) return; // Exit if registration fails due to rate limits

    const userToken = `Bearer ${regRes.json('token')}`;
    const userHeaders = {'Authorization': userToken, 'Content-Type': 'application/json'};

    // Step B: Fetch Available Seats
    const randomPage = Math.floor(Math.random() * 50);
    // Only fetch 50 seats at a time. This reduces the JSON payload by 99%
    let seatsRes = http.get(`${BASE_URL}/events/${data.eventId}/seats?page=${randomPage}&size=50`, { headers: userHeaders });
    if (seatsRes.status !== 200 && seatsRes.status !== 201) return;
    let seats = seatsRes.json('content');
    if (!seats || seats.length < 2) return; // Exit if sold out

    // Pick two random seats to force race conditions across VUs
    const seatIds = [
        seats[Math.floor(Math.random() * seats.length)].id,
        seats[Math.floor(Math.random() * seats.length)].id
    ];

    // Step C: Hold Seats (Pessimistic Redis Lock)
    const holdPayload = JSON.stringify({eventId: eventId, seatIds: seatIds});
    let holdRes = http.post(`${BASE_URL}/seats/hold`, holdPayload, {headers: userHeaders});
    if (holdRes.status !== 201) return; // Normal during concurrency; someone else grabbed the lock

    // Step D: Create Booking
    const bookPayload = JSON.stringify({eventId: eventId, seatIds: seatIds});
    let bookRes = http.post(`${BASE_URL}/bookings`, bookPayload, {headers: userHeaders});
    if (bookRes.status !== 200 && bookRes.status !== 201) return;
    let bookingId = bookRes.json('id');

    // Step E: Initialize Payment
    const payInitPayload = JSON.stringify({bookingId: bookingId, paymentMethod: "Debit Card"});
    const idempotencyKey = generateUUID();

    const paymentHeaders = Object.assign({}, {
        "Idempotency-Key" : idempotencyKey
    }, userHeaders);

    let payRes = http.post(`${BASE_URL}/payments/initialize`, payInitPayload, {headers: paymentHeaders});
    if (payRes.status !== 200 && payRes.status !== 201) return;
    let paymentId = payRes.json('paymentId');

    // Step F: Forge Webhook to Trigger RabbitMQ
    const webhookPayload = JSON.stringify({
        event: "charge.success",
        data: {
            reference: `k6-tx-${__VU}-${__ITER}`,
            metadata: {paymentId: paymentId}
        }
    });

    const forgedSignature = crypto.hmac('sha512', PAYSTACK_SECRET, webhookPayload, 'hex');

    let webhookRes = http.post(`${BASE_URL}/webhooks/paystack`, webhookPayload, {
        headers: {
            'Content-Type': 'application/json',
            'x-paystack-signature': forgedSignature
        }
    });

    // Step G: Assertions
    check(webhookRes, {
        'Webhook accepted (RabbitMQ message published)': (r) => r.status === 200,
    });

    sleep(1); // Wait 1 second before looping again
}