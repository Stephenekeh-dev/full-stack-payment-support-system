# Open Heaven Sanctuary – Payment Module

Complete Paystack payment integration with Spring Boot backend.

---

## Project Structure

```
payment-backend/
├── pom.xml
├── support.html              ← Updated frontend (copy to your website folder)
├── support.css               ← Updated CSS   (copy to your css/ folder)
├── thanks.html               ← Thank-you page (copy to your website folder)
└── src/main/
    ├── java/com/openheavensanctuary/payment/
    │   ├── PaymentServiceApplication.java     ← Entry point
    │   ├── config/
    │   │   └── CorsConfig.java                ← Allows your frontend origin
    │   ├── controller/
    │   │   └── PaymentController.java         ← REST endpoints
    │   ├── model/
    │   │   ├── Payment.java                   ← DB entity
    │   │   ├── PaymentRepository.java         ← JPA repository
    │   │   └── PaymentDTOs.java               ← Request/response objects
    │   └── service/
    │       └── PaystackService.java           ← Paystack API calls
    └── resources/
        └── application.properties             ← Keys & config
```

---

## Quick Setup

### 1. Get your Paystack keys

1. Log in at https://dashboard.paystack.com
2. Go to **Settings → API Keys & Webhooks**
3. Copy your **Test Secret Key** and **Test Public Key**

### 2. Add your keys

Open `src/main/resources/application.properties` and replace:

```properties
paystack.secret-key=sk_test_REPLACE_WITH_YOUR_SECRET_KEY
paystack.public-key=pk_test_REPLACE_WITH_YOUR_PUBLIC_KEY
```

### 3. Allow your frontend origin

Still in `application.properties`, update:

```properties
app.cors.allowed-origins=http://localhost:3000,http://127.0.0.1:5500,https://yourdomain.com
```

Add whatever URL your frontend runs on.

### 4. Run the backend

```bash
cd payment-backend
./mvnw spring-boot:run
```

Backend starts on **http://localhost:8080**

### 5. Update frontend backend URL

In `support.html`, find this line and update it:

```javascript
const BACKEND_URL = "http://localhost:8080";
```

---

## API Endpoints

| Method | Endpoint                          | What it does                              |
|--------|-----------------------------------|-------------------------------------------|
| GET    | `/api/payment/config`             | Returns Paystack public key to frontend   |
| POST   | `/api/payment/initialize`         | Creates reference, saves PENDING to DB    |
| GET    | `/api/payment/verify/{reference}` | Verifies with Paystack, updates DB record |

### POST /api/payment/initialize

**Request body:**
```json
{
  "donorName":   "Pastor Joel Keke",
  "email":       "joel@example.com",
  "amountNaira": 5000
}
```

**Response:**
```json
{
  "success":   true,
  "reference": "OHS_1716000000000_4523",
  "message":   "Reference created. Open Paystack popup with this reference."
}
```

### GET /api/payment/verify/OHS_1716000000000_4523

**Response:**
```json
{
  "success":     true,
  "status":      "SUCCESS",
  "reference":   "OHS_1716000000000_4523",
  "amountNaira": 5000,
  "donorName":   "Pastor Joel Keke",
  "channel":     "card",
  "message":     "Thank you! Your donation of ₦5000 has been received."
}
```

---

## View Donations (H2 Console)

While running in dev mode, open:
**http://localhost:8080/h2-console**

- JDBC URL: `jdbc:h2:mem:ohsdb`
- Username: `sa`
- Password: *(leave blank)*

Run: `SELECT * FROM PAYMENTS ORDER BY CREATED_AT DESC;`

---

## Going Live (Production Checklist)

- [ ] Replace `sk_test_` / `pk_test_` with your **live keys** (`sk_live_` / `pk_live_`)
- [ ] Swap H2 for MySQL or PostgreSQL in `application.properties`
- [ ] Update `app.cors.allowed-origins` to your real domain
- [ ] Update `BACKEND_URL` in `support.html` to your server's URL
- [ ] Deploy the Spring Boot JAR to a VPS or cloud provider
- [ ] Set up HTTPS (Let's Encrypt is free)
- [ ] Set Paystack webhook URL in your dashboard to:
  `https://yourdomain.com/api/payment/verify`

---

## Payment Flow (How It Works)

```
Browser                     Backend                   Paystack
  │                             │                          │
  │── POST /initialize ────────>│                          │
  │                             │── Save PENDING to DB     │
  │<── { reference } ──────────│                          │
  │                             │                          │
  │── Open Paystack Popup ──────────────────────────────>│
  │                             │          User pays       │
  │<── popup callback(ref) ─────────────────────────────│
  │                             │                          │
  │── GET /verify/{ref} ───────>│                          │
  │                             │── GET /transaction/verify/{ref} ─>│
  │                             │<── { status: "success" } ────────│
  │                             │── Update DB to SUCCESS   │
  │<── { success: true } ──────│                          │
  │                             │                          │
  │── Redirect to thanks.html   │                          │
```