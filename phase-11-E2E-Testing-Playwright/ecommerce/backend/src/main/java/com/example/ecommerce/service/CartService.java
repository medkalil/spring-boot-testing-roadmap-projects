package com.example.ecommerce.service;

import com.example.ecommerce.dto.AddCartItemRequest;
import com.example.ecommerce.dto.CartItemResponse;
import com.example.ecommerce.dto.CartResponse;
import com.example.ecommerce.dto.UpdateCartItemRequest;
import com.example.ecommerce.entity.CartItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The persistent, database-backed cart.
 *
 * Every quantity and price decision is made HERE, server-side:
 *   - price is read from the Product table at every read (never stored on the
 *     cart row) so totals are always current;
 *   - quantity must be > 0 and <= current stock, or the write is rejected;
 *   - adding a product that already sits in the cart increments the quantities
 *     (the UNIQUE(user_id, product_id) constraint keeps one row per product).
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       UserRepository userRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(String email) {
        return toResponse(cartItemRepository.findByUserEmailOrderByIdAsc(email));
    }

    /** POST /api/cart/items */
    @Transactional
    public CartResponse addItem(String email, AddCartItemRequest request) {
        if (request.productId() == null || request.quantity() == null || request.quantity() <= 0) {
            throw new BadRequestException("productId and a positive quantity are required");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!product.isActive()) {
            throw new ConflictException(product.getName() + " is not available");
        }

        CartItem existing = cartItemRepository
                .findByUserEmailAndProductId(email, request.productId())
                .orElse(null);

        int wanted = request.quantity();
        if (existing != null) {
            // Overflow beyond one row: keep the same row, check cumulative quantity.
            if ((long) existing.getQuantity() + wanted > product.getStock()) {
                throw new ConflictException(onlyXLeft(product));
            }
            existing.incrementQuantity(wanted);
            cartItemRepository.save(existing);
        } else {
            if (wanted > product.getStock()) {
                throw new ConflictException(onlyXLeft(product));
            }
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));
            cartItemRepository.save(new CartItem(user, product, wanted));
        }

        return toResponse(cartItemRepository.findByUserEmailOrderByIdAsc(email));
    }

    /** PATCH /api/cart/items/{itemId} - absolute quantity; 0 or negative is rejected. */
    @Transactional
    public CartResponse updateItem(String email, Long itemId, UpdateCartItemRequest request) {
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BadRequestException("Quantity must be a positive number");
        }

        CartItem item = ownItem(email, itemId);

        if (request.quantity() > item.getProduct().getStock()) {
            throw new ConflictException(onlyXLeft(item.getProduct()));
        }

        item.setQuantity(request.quantity());
        cartItemRepository.save(item);

        return toResponse(cartItemRepository.findByUserEmailOrderByIdAsc(email));
    }

    /** DELETE /api/cart/items/{itemId} */
    @Transactional
    public CartResponse removeItem(String email, Long itemId) {
        cartItemRepository.delete(ownItem(email, itemId));
        return toResponse(cartItemRepository.findByUserEmailOrderByIdAsc(email));
    }

    /**
     * Ownership gate: OTHER customers' cart rows behave like missing rows (404).
     * Without this, a guessed id would let someone read or delete a stranger's
     * cart item.
     */
    private CartItem ownItem(String email, Long itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
        if (!item.getUser().getEmail().equals(email)) {
            throw new ResourceNotFoundException("Cart item not found");
        }
        return item;
    }

    private CartResponse toResponse(List<CartItem> items) {
        List<CartItemResponse> mapped = items.stream().map(item -> {
            BigDecimal unitPrice = item.getProduct().getPrice();
            return new CartItemResponse(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    unitPrice,
                    item.getQuantity(),
                    unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
            );
        }).toList();

        BigDecimal total = mapped.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(mapped, total);
    }

    private String onlyXLeft(Product product) {
        return "Only " + Math.max(0, product.getStock()) + " of " + product.getName() + " left in stock";
    }
}