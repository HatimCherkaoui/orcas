package com.github.orcas.demo.service;

import com.github.orcas.demo.domain.*;
import com.github.orcas.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {
    private final CustomerRepository customers;
    private final InventoryRepository inventory;
    private final OrderRepository orders;
    private final PaymentRepository payments;

    public OrderService(CustomerRepository customers, InventoryRepository inventory,
                        OrderRepository orders, PaymentRepository payments) {
        this.customers = customers;
        this.inventory = inventory;
        this.orders = orders;
        this.payments = payments;
    }

    @Transactional
    public Order createPendingOrder(CreateOrderCommand command) {
        Customer customer = customers.findById(command.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + command.customerId()));
        Order order = new Order(customer, OrderStatus.PENDING_PAYMENT, BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItem item : command.items()) {
            Inventory stock = inventory.findBySku(item.sku())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + item.sku()));
            if (stock.getQuantity() < item.quantity())
                throw new IllegalStateException("Insufficient inventory for " + item.sku());
            stock.setQuantity(stock.getQuantity() - item.quantity());
            OrderItem orderItem = new OrderItem(item.sku(), item.quantity(), item.unitPrice());
            order.addItem(orderItem);
            total = total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        order.setTotalAmount(total);
        return orders.save(order);
    }

    @Transactional(readOnly = true)
    public Order get(long id) { return orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Order not found: " + id)); }

    @Transactional
    public void savePaymentPending(long orderId, String providerPaymentId) {
        Order order = get(orderId);
        payments.save(new Payment(order, order.getTotalAmount(), PaymentStatus.PENDING, providerPaymentId));
    }

    @Transactional
    public void confirmPayment(long orderId, String providerPaymentId) {
        Payment payment = payments.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + providerPaymentId));
        if (!payment.getOrder().getId().equals(orderId)) throw new IllegalStateException("Payment/order mismatch");
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.getOrder().setStatus(OrderStatus.CONFIRMED);
    }

    @Transactional
    public void failPayment(long orderId, String providerPaymentId) {
        Payment payment = payments.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + providerPaymentId));
        payment.setStatus(PaymentStatus.FAILED);
        releaseInventory(payment.getOrder());
        payment.getOrder().setStatus(OrderStatus.CANCELLED);
    }

    @Transactional
    public void markRefundRequired(long orderId, String providerPaymentId) {
        Payment payment = payments.findByProviderPaymentId(providerPaymentId).orElseThrow();
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        payment.getOrder().setStatus(OrderStatus.REFUND_REQUIRED);
    }

    @Transactional
    public void markRefunded(String providerPaymentId) {
        Payment payment = payments.findByProviderPaymentId(providerPaymentId).orElseThrow();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.getOrder().setStatus(OrderStatus.REFUNDED);
    }

    @Transactional
    public void releaseInventory(long orderId) { releaseInventory(get(orderId)); }

    private void releaseInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            Inventory stock = inventory.findBySku(item.getSku()).orElseThrow();
            stock.setQuantity(stock.getQuantity() + item.getQuantity());
        }
    }

    public record CreateOrderCommand(Long customerId, List<CreateOrderItem> items) {}
    public record CreateOrderItem(String sku, int quantity, BigDecimal unitPrice) {}
}
