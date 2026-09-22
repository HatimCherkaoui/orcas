package com.github.orcas.demo.repository;
import com.github.orcas.demo.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {}
