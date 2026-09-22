package com.github.orcas.demo.repository;
import com.github.orcas.demo.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerRepository extends JpaRepository<Customer, Long> {}
