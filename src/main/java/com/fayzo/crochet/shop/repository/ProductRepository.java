package com.fayzo.crochet.shop.repository;

import java.util.List;

import com.fayzo.crochet.shop.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByNameIgnoreCase(String name);
}