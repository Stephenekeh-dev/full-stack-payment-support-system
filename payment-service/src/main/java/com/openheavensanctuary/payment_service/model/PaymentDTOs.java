package com.openheavensanctuary.payment_service.model;

import jakarta.validation.constraints.*;

/**
 * All DTOs in one file for simplicity.
 * Split into separate files as the project grows.
 */
public class PaymentDTOs {

    // ── Inbound: what the frontend POSTs ─────────────────────────────────

    public static class InitializeRequest {

        @NotBlank(message = "Donor name is required")
        private String donorName;

        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        private String email;

        @NotNull(message = "Amount is required")
        @Min(value = 1000, message = "Minimum donation is ₦1,000")
        private Long amountNaira;

        public String getDonorName() { return donorName; }
        public void setDonorName(String donorName) { this.donorName = donorName; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public Long getAmountNaira() { return amountNaira; }
        public void setAmountNaira(Long amountNaira) { this.amountNaira = amountNaira; }
    }

    // ── Outbound: what the backend returns to the frontend ───────────────

    public static class InitializeResponse {
        private boolean success;
        private String reference;
        private String message;

        public InitializeResponse(boolean success, String reference, String message) {
            this.success = success;
            this.reference = reference;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getReference() { return reference; }
        public String getMessage() { return message; }
    }

    public static class VerifyResponse {
        private boolean success;
        private String status;       // SUCCESS | FAILED | PENDING
        private String reference;
        private Long amountNaira;
        private String donorName;
        private String channel;
        private String message;

        // Builder-style setters for readability
        public VerifyResponse success(boolean v)          { this.success = v;    return this; }
        public VerifyResponse status(String v)            { this.status = v;     return this; }
        public VerifyResponse reference(String v)         { this.reference = v;  return this; }
        public VerifyResponse amountNaira(Long v)         { this.amountNaira = v; return this; }
        public VerifyResponse donorName(String v)         { this.donorName = v;  return this; }
        public VerifyResponse channel(String v)           { this.channel = v;    return this; }
        public VerifyResponse message(String v)           { this.message = v;    return this; }

        public boolean isSuccess()   { return success; }
        public String getStatus()    { return status; }
        public String getReference() { return reference; }
        public Long getAmountNaira() {
            return amountNaira;
        }
        public String getDonorName() { return donorName; }
        public String getChannel()   { return channel; }
        public String getMessage()   { return message; }
    }
}