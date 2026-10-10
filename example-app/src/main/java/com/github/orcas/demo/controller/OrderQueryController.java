package com.github.orcas.demo.controller;

import com.github.orcas.demo.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderQueryController {
    private final OrderService orders;

    public OrderQueryController(OrderService orders) {
        this.orders = orders;
    }
    @GetMapping("/{id}")
    public OrderView get(@PathVariable long id) {
        var o = orders.get(id);
        var items = o.getItems().stream()
                .map(i -> new Item(i.getSku(), i.getQuantity()))
                .toList();
        return new OrderView(o.getId(), o.getStatus().name(), o.getTotalAmount(), items);
    }
    public record OrderView(Long id, String status, java.math.BigDecimal totalAmount, java.util.List<Item> items) {}
    public record Item(String sku, int quantity) {}
}
