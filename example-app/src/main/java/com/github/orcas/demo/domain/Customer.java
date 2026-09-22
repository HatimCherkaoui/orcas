package com.github.orcas.demo.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "customers", comment = "Customers table", indexes = {
        @Index(name = "idx_email", columnList = "email")
})
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, comment = "Name of the customer")
    private String name;

    @Column(nullable = false, unique = true, comment = "Email of the customer")
    private String email;

    protected Customer() {
    }

    public Customer(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
