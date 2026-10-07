package com.example.orders.service;

import com.example.orders.dto.CreateProductRequest;
import com.example.orders.dto.ProductResponse;
import com.example.orders.entity.Product;
import com.example.orders.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {

        if (request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Product price cannot be negative");
        }

        Product product = new Product(
                request.name(),
                request.price()
        );

        Product saved = productRepository.save(product);

        return new ProductResponse(
                saved.getId(),
                saved.getName(),
                saved.getPrice()
        );
    }
}