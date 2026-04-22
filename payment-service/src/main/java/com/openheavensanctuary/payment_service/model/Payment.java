package com.openheavensanctuary.payment_service.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persists every Paystack payment attempt so you can audit
 * and reconcile donations from the H2 console (or any SQL client in prod).
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Donor full name (collected from the form) */
    private String donorName;

    /** Donor email – also used as the Paystack customer identifier */
    private String email;

    /** Amount in Naira (NOT kobo – we store human-readable) */
    private Long amountNaira;

    /** Unique reference we generate and pass to Paystack */
    @Column(unique = true)
    private String reference;

    /**
     * Status values:
     *   PENDING   – transaction created, user hasn't paid yet
     *   SUCCESS   – Paystack confirmed payment
     *   FAILED    – Paystack verification returned a non-success status
     *   CANCELLED – user closed the payment popup / redirect
     */
    private String status;

    /** Raw status message returned by Paystack (e.g. "success", "failed") */
    private String paystackStatus;

    /** Paystack gateway response message */
    private String gatewayResponse;

    /** ISO 4217 currency code – NGN for naira */
    private String currency;

    /** Channel used: card, bank, ussd, qr, mobile_money, bank_transfer */
    private String channel;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (currency == null) currency = "NGN";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getDonorName() { return donorName; }
    public void setDonorName(String donorName) { this.donorName = donorName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Long getAmountNaira() { return amountNaira; }
    public void setAmountNaira(Long amountNaira) { this.amountNaira = amountNaira; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaystackStatus() { return paystackStatus; }
    public void setPaystackStatus(String paystackStatus) { this.paystackStatus = paystackStatus; }

    public String getGatewayResponse() { return gatewayResponse; }
    public void setGatewayResponse(String gatewayResponse) { this.gatewayResponse = gatewayResponse; }

    public String getCurrency() {
        return currency;
    }
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}