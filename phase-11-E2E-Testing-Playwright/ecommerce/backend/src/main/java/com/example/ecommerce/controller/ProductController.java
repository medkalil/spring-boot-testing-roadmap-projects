package com.example.ecommerce.controller;

import com.example.ecommerce.dto.ProductResponse;
import com.example.ecommerce.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The public catalog - no token required (SecurityConfig). */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> listProducts() {
        return productService.listActiveProducts();
    }

    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable Long productId) {
        return productService.getActiveProduct(productId);
    }
}