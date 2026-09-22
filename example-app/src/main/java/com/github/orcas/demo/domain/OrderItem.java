package com.github.orcas.demo.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items", comment = "Order items table", indexes = {
        @Index(name = "idx_order_id", columnList = "order_id"),
})
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", comment = "Reference to the order")
    private Order order;

    @Column(nullable = false, comment = "Stock keeping unit")
    private String sku;

    @Column(nullable = false, comment = "Quantity of the product ordered")
    private int quantity;

    @Column(nullable = false, comment = "Unit price of the product")
    private BigDecimal unitPrice;

    protected OrderItem() {
    }

    public OrderItem(String sku, int quantity, BigDecimal unitPrice) {
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
