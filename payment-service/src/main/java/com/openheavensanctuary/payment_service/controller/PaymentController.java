package com.openheavensanctuary.payment_service.controller;

import com.openheavensanctuary.payment_service.model.PaymentDTOs.*;
import com.openheavensanctuary.payment_service.service.PaystackService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaystackService paystackService;

    public PaymentController(PaystackService paystackService) {
        this.paystackService = paystackService;
    }

    /**
     * GET /api/payment/config
     *
     * Returns the Paystack public key so the frontend can stay key-agnostic.
     * Switch between test / live just by updating application.properties.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "publicKey", paystackService.getPublicKey()
        ));
    }

    /**
     * POST /api/payment/initialize
     *
     * Body (JSON):
     * {
     *   "donorName": "Pastor Joel Keke",
     *   "email":     "joel@example.com",
     *   "amountNaira": 5000
     * }
     *
     * Response (JSON):
     * {
     *   "success":   true,
     *   "reference": "OHS_1716000000000_4523",
     *   "message":   "Reference created. Open Paystack popup with this reference."
     * }
     */
    @PostMapping("/initialize")
    public ResponseEntity<InitializeResponse> initializePayment(
            @Valid @RequestBody InitializeRequest request) {

        log.info("Payment init request – donor: {}, email: {}, amount: ₦{}",
                request.getDonorName(), request.getEmail(), request.getAmountNaira());

        InitializeResponse response = paystackService.initializePayment(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/payment/verify/{reference}
     *
     * Called by the frontend after Paystack popup callback fires.
     * The reference comes from the popup callback object.
     *
     * Response (JSON):
     * {
     *   "success":     true,
     *   "status":      "SUCCESS",
     *   "reference":   "OHS_1716000000000_4523",
     *   "amountNaira": 5000,
     *   "donorName":   "Pastor Joel Keke",
     *   "channel":     "card",
     *   "message":     "Thank you! Your donation of ₦5000 has been received."
     * }
     */
    @GetMapping("/verify/{reference}")
    public ResponseEntity<VerifyResponse> verifyPayment(
            @PathVariable String reference) {

        log.info("Verify request for reference: {}", reference);

        VerifyResponse response = paystackService.verifyPayment(reference);

        // Return 200 always – the `success` field tells the frontend what happened.
        // This prevents the browser from treating a failed payment as an HTTP error.
        return ResponseEntity.ok(response);
    }

    /**
     * Global validation error handler – returns a readable message
     * when @Valid constraints fail (e.g. amount < 1000, bad email).
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {

        String firstError = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");

        return ResponseEntity.badRequest().body(Map.of("message", firstError));
    }
}