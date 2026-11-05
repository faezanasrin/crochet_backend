package com.fayzo.crochet.shop.repository;

import com.fayzo.crochet.shop.model.ProductReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    List<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId);
    boolean existsByProductIdAndCustomerEmailIgnoreCase(Long productId, String customerEmail);
    void deleteByProductId(Long productId);
}
