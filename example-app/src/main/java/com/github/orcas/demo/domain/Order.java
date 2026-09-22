package com.github.orcas.demo.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", comment = "Orders table", indexes = {
        @Index(name = "idx_customer_id", columnList = "customer_id"),
        @Index(name = "idx_status", columnList = "status")
})
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", comment = "Reference to the customer")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, comment = "Status of the order")
    private OrderStatus status;

    @Column(nullable = false, comment = "Total amount of the order")
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(Customer customer, OrderStatus status, BigDecimal totalAmount) {
        this.customer = customer;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
