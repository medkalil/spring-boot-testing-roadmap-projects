package com.example.ecommerce.repository;

import com.example.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** The public catalog - active products only, stable order. */
    List<Product> findByActiveTrueOrderByIdAsc();

    /** Catalog detail. The inactive seed product is deliberately invisible. */
    Optional<Product> findByActiveTrueAndId(Long id);
}