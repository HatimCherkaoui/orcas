package com.github.orcas.demo.repository;
import com.github.orcas.demo.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderRepository extends JpaRepository<Order, Long> {}
