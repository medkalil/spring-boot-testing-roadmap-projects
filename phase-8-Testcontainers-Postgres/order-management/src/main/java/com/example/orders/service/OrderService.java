package com.example.orders.service;

import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.CustomerResponse;
import com.example.orders.dto.OrderItemRequest;
import com.example.orders.dto.OrderItemResponse;
import com.example.orders.dto.OrderResponse;
import com.example.orders.entity.Customer;
import com.example.orders.entity.Order;
import com.example.orders.entity.OrderItem;
import com.example.orders.entity.Product;
import com.example.orders.exception.ResourceNotFoundException;
import com.example.orders.repository.CustomerRepository;
import com.example.orders.repository.OrderRepository;
import com.example.orders.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public OrderService(
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository
    ) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    // Phase 8.9 — the main business flow: HTTP -> controller -> service -> repositories -> PostgreSQL
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // 1) customer must exist, otherwise nothing is written at all (404)
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found: " + request.customerId()
                ));

        Order order = new Order(customer);

        // 2) The order is saved BEFORE the products are resolved on purpose.
        //    With GenerationType.IDENTITY the INSERT reaches PostgreSQL immediately,
        //    so if a product is missing the exception below leaves a partially
        //    written order behind and proves the @Transactional rollback works
        //    against a real database (Phase 8.10).
        orderRepository.save(order);

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {

            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + itemRequest.productId()
                    ));

            if (itemRequest.quantity() == null || itemRequest.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }

            // unitPrice is a snapshot of the current product price
            BigDecimal unitPrice = product.getPrice();

            order.addItem(new OrderItem(product, itemRequest.quantity(), unitPrice));

            total = total.add(unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }

        // 3) items are persisted through cascade = CascadeType.ALL on Order.items
        order.setTotal(total);
        orderRepository.save(order);

        return toResponse(order);
    }

    // Phase 8.9 — read back the whole aggregate
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderId
                ));

        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTotal(),
                new CustomerResponse(
                        order.getCustomer().getId(),
                        order.getCustomer().getName(),
                        order.getCustomer().getEmail()
                ),
                order.getItems().stream()
                        .map(this::toItemResponse)
                        .toList()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }
}