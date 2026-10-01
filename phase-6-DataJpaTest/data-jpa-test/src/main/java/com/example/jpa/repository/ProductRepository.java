package com.example.jpa.repository;

import com.example.jpa.entity.Category;
import com.example.jpa.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    List<Product> findByName(String name);

    List<Product> findByPriceBetween(
            BigDecimal min,
            BigDecimal max
    );

    List<Product> findByStockGreaterThan(Integer stock);

// Phase 6.2 — Derived queries
    List<Product> findByNameContaining(String name);
    List<Product> findByNameContainingIgnoreCase(String name);
    List<Product> findByPriceLessThan(BigDecimal price);
    List<Product> findByStockLessThan(Integer stock);

// Phase 6.3 — Relationships
    List<Product> findByCategory(Category category);
    List<Product> findByCategoryName(String name);

// Phase 6.4 — Constraints : add unique sku fields to the product entity

// Phase 6.5 — Custom queries
    @Query("SELECT p FROM Product p WHERE p.price > :price")
    List<Product> findExpensiveProducts(@Param("price") BigDecimal price);

    @Query("SELECT p FROM Product p WHERE p.price BETWEEN :minPrice AND :maxPrice AND p.stock = :stock")
    List<Product> searchProducts(
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        @Param("stock") Integer stock
    );

// Phase 6.6 — Pagination and sorting
    Page<Product> findByCategory(Category category,Pageable pageable);

}