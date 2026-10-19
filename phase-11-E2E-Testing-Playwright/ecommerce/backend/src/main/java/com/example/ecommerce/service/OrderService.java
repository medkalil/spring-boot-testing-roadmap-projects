package com.example.ecommerce.service;

import com.example.ecommerce.dto.OrderItemResponse;
import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.entity.CartItem;
import com.example.ecommerce.entity.Order;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.exception.BadRequestException;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The one place that creates ORDERS. Everything below happens in a SINGLE
 * transaction - if any step fails, nothing is persisted:
 *
 *   read cart -> validate stock & availability -> snapshot prices from the
 *   Product table -> build Order + OrderItems with the SERVER-computed total
 *   -> decrement stock -> clear the cart.
 *
 * Two E2E scenarios depend on this atomicity: "checkout twice rapidly creates
 * no duplicate order" (the second call finds an empty cart) and "a failed
 * checkout leaves no partial order".
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,
                        CartItemRepository cartItemRepository,
                        ProductRepository productRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrderResponse createOrder(String email, OrderRequest request) {
        if (request.shippingAddress() == null || request.shippingAddress().isBlank()) {
            throw new BadRequestException("Shipping address is required");
        }

        List<CartItem> cartItems = cartItemRepository.findByUserEmailOrderByIdAsc(email);
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user no longer exists"));

        Order order = new Order(user, request.shippingAddress().trim(), BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();

            if (!product.isActive()) {
                throw new ConflictException(product.getName() + " is no longer available");
            }
            if (cartItem.getQuantity() > product.getStock()) {
                throw new ConflictException(
                        "Only " + Math.max(0, product.getStock()) + " of " + product.getName() + " left in stock");
            }

            BigDecimal unitPrice = product.getPrice();
            total = total.add(unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            order.addItem(new OrderItem(product.getId(), product.getName(), cartItem.getQuantity(), unitPrice));
        }

        order.setTotal(total);
        orderRepository.save(order);

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            product.setStock(product.getStock() - cartItem.getQuantity());
            productRepository.save(product);
        }

        cartItemRepository.deleteByUserEmail(email);

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(String email) {
        return orderRepository.findByUserEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * GET /api/orders/{id} - owner-scoped. Another customer's order id matches
     * 0 rows (findByIdAndUserEmail) and is answered as 404, concealing that
     * the order even exists.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String email, Long orderId) {
        Order order = orderRepository.findByIdAndUserEmail(orderId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotal(),
                order.getShippingAddress(),
                order.getCreatedAt(),
                items
        );
    }
}