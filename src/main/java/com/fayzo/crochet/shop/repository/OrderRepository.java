package com.fayzo.crochet.shop.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.fayzo.crochet.shop.model.CustomerOrder;

@Repository
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    // Finds all orders belonging to a customer, newest first
    List<CustomerOrder> findByCustomerEmailOrderByIdDesc(String customerEmail);
    List<CustomerOrder> findByCustomerEmailIgnoreCaseOrderByIdDesc(String customerEmail);
}