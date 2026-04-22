package com.openheavensanctuary.payment_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openheavensanctuary.payment_service.model.Payment;
import com.openheavensanctuary.payment_service.model.PaymentDTOs.*;
import com.openheavensanctuary.payment_service.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Handles all communication with the Paystack REST API.
 *
 * Paystack docs: https://paystack.com/docs/api/
 *
 * Two flows are supported:
 *
 *  1. Inline / Popup (recommended for this site)
 *     - Frontend calls  POST /api/payment/initialize  → backend creates a
 *       reference and saves a PENDING record.
 *     - Frontend opens the Paystack popup with that reference.
 *     - After success Paystack calls the popup callback.
 *     - Frontend calls  GET  /api/payment/verify/{ref}  → backend confirms
 *       with Paystack and marks the record SUCCESS or FAILED.
 *
 *  2. Redirect flow (fallback)
 *     - Backend calls Paystack's /transaction/initialize and gets an
 *       authorization_url, then redirects the user there.
 */
@Service
public class PaystackService {

    private static final Logger log = LoggerFactory.getLogger(PaystackService.class);

    private static final String PAYSTACK_INIT_URL   = "https://api.paystack.co/transaction/initialize";
    private static final String PAYSTACK_VERIFY_URL = "https://api.paystack.co/transaction/verify/";

    @Value("${paystack.secret-key}")
    private String secretKey;

    @Value("${paystack.public-key}")
    private String publicKey;

    @Value("${app.payment.callback-url}")
    private String callbackUrl;

    private final PaymentRepository paymentRepository;
    private final ObjectMapper       objectMapper;
    private final HttpClient         httpClient;

    public PaystackService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
        this.objectMapper      = new ObjectMapper();
        this.httpClient        = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  STEP 1 – Create a reference and persist a PENDING payment record.
    //  The frontend will use this reference when opening the Paystack popup.
    // ─────────────────────────────────────────────────────────────────────

    public InitializeResponse initializePayment(InitializeRequest req) {

        String reference = generateReference();

        // Persist the pending record immediately so we can track it
        Payment payment = new Payment();
        payment.setDonorName(req.getDonorName());
        payment.setEmail(req.getEmail());
        payment.setAmountNaira(req.getAmountNaira());
        payment.setReference(reference);
        payment.setStatus("PENDING");
        paymentRepository.save(payment);

        log.info("Payment record created – ref: {}, donor: {}, amount: ₦{}",
                reference, req.getDonorName(), req.getAmountNaira());

        return new InitializeResponse(true, reference,
                "Reference created. Open Paystack popup with this reference.");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  STEP 2 – Verify with Paystack and update the DB record.
    //  Called by the frontend (or Paystack webhook) after payment attempt.
    // ─────────────────────────────────────────────────────────────────────

    public VerifyResponse verifyPayment(String reference) {

        Payment payment = paymentRepository.findByReference(reference)
                .orElseThrow(() -> new RuntimeException(
                        "No payment found for reference: " + reference));

        // If we already processed this reference, just return cached result
        if ("SUCCESS".equals(payment.getStatus())) {
            return buildVerifyResponse(payment, true,
                    "Payment already verified and confirmed.");
        }

        // Call Paystack verify endpoint
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PAYSTACK_VERIFY_URL + reference))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());

            log.debug("Paystack verify response: {}", response.body());

            if (!root.path("status").asBoolean()) {
                String msg = root.path("message").asText("Verification call failed");
                payment.setStatus("FAILED");
                payment.setPaystackStatus("api_error");
                paymentRepository.save(payment);
                return new VerifyResponse()
                        .success(false).status("FAILED")
                        .reference(reference).message(msg);
            }

            JsonNode data           = root.path("data");
            String paystackStatus   = data.path("status").asText();       // "success"
            String gatewayResponse  = data.path("gateway_response").asText();
            String channel          = data.path("channel").asText();

            payment.setPaystackStatus(paystackStatus);
            payment.setGatewayResponse(gatewayResponse);
            payment.setChannel(channel);

            if ("success".equalsIgnoreCase(paystackStatus)) {
                payment.setStatus("SUCCESS");
                paymentRepository.save(payment);

                log.info("Payment SUCCESS – ref: {}, donor: {}, amount: ₦{}, channel: {}",
                        reference, payment.getDonorName(), payment.getAmountNaira(), channel);

                return buildVerifyResponse(payment, true,
                        "Thank you! Your donation of ₦" + payment.getAmountNaira() + " has been received.");

            } else {
                payment.setStatus("FAILED");
                paymentRepository.save(payment);

                log.warn("Payment FAILED – ref: {}, paystackStatus: {}, gateway: {}",
                        reference, paystackStatus, gatewayResponse);

                return new VerifyResponse()
                        .success(false).status("FAILED")
                        .reference(reference)
                        .message("Payment was not successful: " + gatewayResponse);
            }

        } catch (Exception ex) {
            log.error("Error verifying payment ref {}: {}", reference, ex.getMessage(), ex);
            return new VerifyResponse()
                    .success(false).status("ERROR")
                    .reference(reference)
                    .message("An error occurred while verifying your payment. Please contact support.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  OPTIONAL: Full redirect flow – backend initializes with Paystack
    //  and returns the authorization_url. Useful if you want server-side
    //  initialization rather than passing the reference from the frontend.
    // ─────────────────────────────────────────────────────────────────────

    public String initializeAndGetAuthorizationUrl(InitializeRequest req) throws Exception {

        String reference = generateReference();

        // Save pending record
        Payment payment = new Payment();
        payment.setDonorName(req.getDonorName());
        payment.setEmail(req.getEmail());
        payment.setAmountNaira(req.getAmountNaira());
        payment.setReference(reference);
        paymentRepository.save(payment);

        // Build Paystack request body
        Map<String, Object> body = Map.of(
                "email",        req.getEmail(),
                "amount",       req.getAmountNaira() * 100,   // kobo
                "reference",    reference,
                "currency",     "NGN",
                "callback_url", callbackUrl,
                "metadata", Map.of(
                        "donor_name", req.getDonorName(),
                        "custom_fields", new Object[]{
                                Map.of(
                                        "display_name",  "Donor Name",
                                        "variable_name", "donor_name",
                                        "value",         req.getDonorName()
                                )
                        }
                )
        );

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PAYSTACK_INIT_URL))
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = objectMapper.readTree(response.body());

        if (!root.path("status").asBoolean()) {
            throw new RuntimeException("Paystack init failed: " +
                    root.path("message").asText());
        }

        return root.path("data").path("authorization_url").asText();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────

    private String generateReference() {
        return "OHS_" + System.currentTimeMillis() + "_" +
                (int)(Math.random() * 9000 + 1000);
    }

    private VerifyResponse buildVerifyResponse(Payment p, boolean success, String message) {
        return new VerifyResponse()
                .success(success)
                .status(p.getStatus())
                .reference(p.getReference())
                .amountNaira(p.getAmountNaira())
                .donorName(p.getDonorName())
                .channel(p.getChannel())
                .message(message);
    }

    public String getPublicKey() { return publicKey; }
}
