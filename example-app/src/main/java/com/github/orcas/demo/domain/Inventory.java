package com.github.orcas.demo.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory", comment = "Inventory table", indexes = {
        @Index(name = "idx_sku", columnList = "sku")
})
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, comment = "Stock Keeping Unit")
    private String sku;

    @Column(nullable = false, comment = "Product name")
    private String productName;

    @Column(nullable = false, comment = "Quantity of the product in stock")
    private int quantity;

    protected Inventory() {
    }

    public Inventory(String sku, String productName, int quantity) {
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
