package com.example.ecommerce.repository;

import com.example.ecommerce.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** The caller's whole cart (caller = JWT subject = user's email). */
    List<CartItem> findByUserEmailOrderByIdAsc(String email);

    /** "Is product X already in this cart?" - drives add-vs-increment logic. */
    Optional<CartItem> findByUserEmailAndProductId(String email, Long productId);

    /** Clears the cart after a successful checkout (must run inside a transaction). */
    void deleteByUserEmail(String email);
}