package com.github.orcas.demo.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "payments", comment = "Payments table", indexes = {
        @Index(name = "idx_provider_payment_id", columnList = "provider_payment_id")
})
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", unique = true, comment = "Reference to the order")
    private Order order;

    @Column(nullable = false, comment = "Amount of the payment")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, comment = "Status of the payment")
    private PaymentStatus status;

    @Column(unique = true, comment = "Provider payment ID")
    private String providerPaymentId;

    protected Payment() {
    }

    public Payment(Order order, BigDecimal amount, PaymentStatus status, String providerPaymentId) {
        this.order = order;
        this.amount = amount;
        this.status = status;
        this.providerPaymentId = providerPaymentId;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public void setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
    }
}
