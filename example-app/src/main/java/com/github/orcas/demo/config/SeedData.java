package com.github.orcas.demo.config;

import com.github.orcas.demo.domain.*;
import com.github.orcas.demo.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SeedData implements CommandLineRunner {
    private final CustomerRepository customers;
    private final InventoryRepository inventory;

    public SeedData(CustomerRepository customers, InventoryRepository inventory) {
        this.customers = customers;
        this.inventory = inventory;
    }
    @Override public void run(String... args) {
        if (customers.count() == 0) customers.save(new Customer("Alice Example", "alice@example.com"));
        if (inventory.count() == 0) inventory.saveAll(java.util.List.of(
            new Inventory("ORCA-BOOK", "Orca Workflow Book", 20),
            new Inventory("ORCA-MUG", "Orca Mug", 10)));
    }
}
