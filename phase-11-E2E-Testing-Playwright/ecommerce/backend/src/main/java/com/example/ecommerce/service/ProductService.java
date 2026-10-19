package com.example.ecommerce.service;

import com.example.ecommerce.dto.ProductResponse;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The public catalog. No authentication required - SecurityConfig permits
 * /api/products/**. The inactive seed product is never returned, so a browser
 * can never even "see" a product that is unavailable.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listActiveProducts() {
        return productRepository.findByActiveTrueOrderByIdAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getActiveProduct(Long productId) {
        Product product = productRepository.findByActiveTrueAndId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));
        return toResponse(product);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock()
        );
    }
}