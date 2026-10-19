package com.example.ecommerce.controller;

import com.example.ecommerce.dto.AddCartItemRequest;
import com.example.ecommerce.dto.CartResponse;
import com.example.ecommerce.dto.UpdateCartItemRequest;
import com.example.ecommerce.service.CartService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * All cart endpoints require a valid token. Authentication.getName() is the
 * JWT subject = the user's EMAIL, the value every cart service method uses to
 * scope the cart to exactly one customer.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(Authentication authentication) {
        return cartService.getCart(authentication.getName());
    }

    @PostMapping("/items")
    public CartResponse addItem(Authentication authentication, @RequestBody AddCartItemRequest request) {
        return cartService.addItem(authentication.getName(), request);
    }

    @PatchMapping("/items/{itemId}")
    public CartResponse updateItem(Authentication authentication,
                                   @PathVariable Long itemId,
                                   @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(authentication.getName(), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(Authentication authentication, @PathVariable Long itemId) {
        return cartService.removeItem(authentication.getName(), itemId);
    }
}