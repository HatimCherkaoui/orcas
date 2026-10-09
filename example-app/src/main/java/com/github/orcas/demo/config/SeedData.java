package com.github.orcas.demo.config;

import com.github.orcas.demo.domain.Customer;
import com.github.orcas.demo.domain.Inventory;
import com.github.orcas.demo.repository.CustomerRepository;
import com.github.orcas.demo.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SeedData implements CommandLineRunner {
    private final CustomerRepository customers;
    private final InventoryRepository inventory;

    @Value("${demo.inventory.seed-quantity:1000}")
    private int seedQuantity;

    public SeedData(CustomerRepository customers, InventoryRepository inventory) {
        this.customers = customers;
        this.inventory = inventory;
    }
    @Override public void run(String... args) {
        if (customers.count() == 0) customers.save(new Customer("Alice Example", "alice@example.com"));
        ensureInventory("ORCA-BOOK", "Orca Workflow Book");
        ensureInventory("ORCA-MUG", "Orca Mug");
    }

    private void ensureInventory(String sku, String name) {
        var stock = inventory.findBySku(sku).orElseGet(() -> new Inventory(sku, name, 0));
        if (stock.getQuantity() < seedQuantity) {
            stock.setQuantity(seedQuantity);
            inventory.save(stock);
        }
    }
}
