package com.github.orcas.demo.repository;

import com.github.orcas.demo.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    @org.springframework.data.jpa.repository.Query("select p from Payment p join fetch p.order where p.providerPaymentId = :providerPaymentId")
    Optional<Payment> findByProviderPaymentId(@org.springframework.data.repository.query.Param("providerPaymentId") String providerPaymentId);

}
