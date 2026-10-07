package com.example.orders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.orders.dto.CreateCustomerRequest;
import com.example.orders.dto.CreateOrderRequest;
import com.example.orders.dto.CreateProductRequest;
import com.example.orders.dto.OrderItemRequest;
import com.example.orders.entity.Customer;
import com.example.orders.entity.Order;
import com.example.orders.entity.OrderItem;
import com.example.orders.entity.Product;
import com.example.orders.exception.ResourceNotFoundException;
import com.example.orders.repository.CustomerRepository;
import com.example.orders.repository.OrderItemRepository;
import com.example.orders.repository.OrderRepository;
import com.example.orders.repository.ProductRepository;
import com.example.orders.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/*
 * Phase 8 — Testcontainers + PostgreSQL
 *
 * Layer under test (full integration):
 *
 *   HTTP -> Controller -> Service -> Repository -> Hibernate -> JDBC -> PostgreSQL
 *
 * How the wiring works:
 *
 * 1) @Testcontainers            -> enables the Testcontainers/JUnit lifecycle
 * 2) @Container (static)        -> PostgreSQL 16 container, started once for the whole
 *                                  test class and destroyed after the last test.
 *                                  static = shared by every test method (much faster).
 * 3) @DynamicPropertySource     -> Spring Boot never hardcodes localhost:5432.
 *                                  The container picks a free host port dynamically,
 *                                  so the JDBC URL / user / password are read from the
 *                                  running container at context startup.
 *
 * Spring startup order:
 *
 *   JUnit -> Testcontainers -> Docker -> PostgreSQL ready
 *         -> @DynamicPropertySource -> DataSource -> Flyway (V1+V2+V3)
 *         -> Hibernate (ddl-auto=validate, it does NOT create tables)
 *
 * The application.properties values (localhost:5432) are overridden here,
 * so tests never touch a manually started PostgreSQL.
 *
 * Scenarios to practise (Phase 8.9 - 8.12):
 *   1) PostgreSQL connection (DataSource/Hikari against the container)
 *   2) create customer          -> POST /api/customers
 *   3) create product           -> POST /api/products
 *   4) create order             -> POST /api/orders   (2 x 100 + 3 x 50 = 350)
 *   5) retrieve order           -> GET  /api/orders/{id}
 *   6) invalid customer         -> 404 and no order row persisted
 *   7) invalid product          -> exception + transaction rollback, no order row
 *   8) test isolation / cleanup between tests (Phase 8.11)
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc 
@Transactional 
class OrderIntegrationTest {

    @Autowired 
    private MockMvc mockMvc;

    @Autowired 
    private CustomerRepository customerRepository;

    @Autowired 
    private ProductRepository productRepository;

    @Autowired 
    private OrderRepository orderRepository;

    @Autowired 
    private OrderItemRepository orderItemRepository;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }


    private String asJsonString(Object obj) throws Exception {
        return new ObjectMapper().writeValueAsString(obj);
    }

//  *   1) PostgreSQL connection (DataSource/Hikari against the container)
    @Autowired
    private DataSource dataSource;

    @Test
    void shouldConnectToPostgreSQL() throws Exception {

        try (Connection connection = dataSource.getConnection()) {

            assertTrue(connection.isValid(2));
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }
    }


//  *   2) create customer          -> POST /api/customers
    @Test
    void shouldCreateCustomerSuccessfully() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest("John Doe", "jhone@gmail.com");

        mockMvc.perform(
            post("/api/customers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(request))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("John Doe"))
        .andExpect(jsonPath("$.email").value("jhone@gmail.com"));

        assertEquals(1, customerRepository.count());
    }

    // Test isolation with Transaction
    // in the above @Transactional we rollback the transaction after the test that stores customer
    // 8) test isolation / cleanup between tests (Phase 8.11)
    @Test
    void testtest() throws Exception {
        assertEquals(0, customerRepository.count());
    }

//  *   3) create product           -> POST /api/products
    @Test
    void shouldCreateProductSuccessfully() throws Exception {
        CreateProductRequest request = new CreateProductRequest("Product 1", BigDecimal.valueOf(100));

        mockMvc.perform(
            post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(request))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Product 1"))
        .andExpect(jsonPath("$.price").value(BigDecimal.valueOf(100)));
    }

//  *   4) create order             -> POST /api/orders   (2 x 100 + 3 x 50 = 350)
    @Test
    void shouldCreateOrderSuccessfully() throws Exception {
        // setup data
        Customer customer = customerRepository.save(
            new Customer("John Doe", "jhone@gmail.com")
        );

        Product productbo100 = productRepository.save(
            new Product("Product 1", BigDecimal.valueOf(100))
        );
        Product productbo50 = productRepository.save(
            new Product("Product 2", BigDecimal.valueOf(50))
        );
        
        CreateOrderRequest request = new CreateOrderRequest(
            customer.getId(),
            List.of(
                new OrderItemRequest(productbo100.getId(), 2),
                new OrderItemRequest(productbo50.getId(), 3)
            )
        );

        mockMvc.perform(
            post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(request))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.total").value(350));

        Order found = orderRepository.findById(1L).get();
        assertEquals(BigDecimal.valueOf(350), found.getTotal());

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(found.getId());

        assertNotNull(orderItems);
        assertEquals(2, orderItems.size());
        assertEquals(BigDecimal.valueOf(350), 
            orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

//  *   5) retrieve order           -> GET  /api/orders/{id}
    @Test
    void shouldGetOrderSuccessfully() throws Exception {
        Customer customer = customerRepository.save(
            new Customer("John Doe", "jhone@gmail.com")
        );

        Product productbo100 = productRepository.save(
            new Product("Product 1", BigDecimal.valueOf(100))
        );
        Product productbo50 = productRepository.save(
            new Product("Product 2", BigDecimal.valueOf(50))
        );
        
        CreateOrderRequest request = new CreateOrderRequest(
            customer.getId(),
            List.of(
                new OrderItemRequest(productbo100.getId(), 2),
                new OrderItemRequest(productbo50.getId(), 3)
            )
        );

        mockMvc.perform(
            post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(request))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.total").value(350));

        Order order = orderRepository.findAll().get(0);
        

        mockMvc.perform(
            get("/api/orders/" + order.getId())
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(350));
    }

//  *   6) invalid customer
    @Test
    void shouldThrowExceptionWhenInvalidCustomer() throws Exception {
        mockMvc.perform(
            post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(new CreateOrderRequest(999L, List.of(new OrderItemRequest(1L, 1))))) // 999L is not existing customer
        ) 
        .andExpect(status().isNotFound());

        assertEquals(0, orderRepository.count());
    }

//  *   7) invalid product          -> exception + transaction rollback, no order row saved.
    // to test this you need to comment all the other test + the @Transactional
    // @Test
    // void shouldRollbackOrderWhenProductDoesNotExist() {
    //     Customer customer = new Customer(
    //             "John",
    //             "john@test.com"
    //     );
    //     customer = customerRepository.save(customer);
    //     Long nonExistingProductId = 999999L;

    //     CreateOrderRequest request = new CreateOrderRequest(customer.getId(), List.of(new OrderItemRequest(nonExistingProductId,2)));

    //     assertThrows(ResourceNotFoundException.class,() -> orderService.createOrder(request));

    //     assertEquals(0, orderRepository.count());
    // }

    // also this with mockmvc, also: to test this you need to comment all the other test + the @Transactional
    // @Test
    // void shouldRollbackOrderWhenProductDoesNotExistWithMockMvc() throws Exception {
    //     Customer customer = new Customer("John", "john@test.com");

    //     customer = customerRepository.save(customer);

    //     String requestBody = """
    //             {
    //                 "customerId": %d,
    //                 "items": [
    //                     {
    //                         "productId": 999999,
    //                         "quantity": 2
    //                     }
    //                 ]
    //             }
    //             """.formatted(customer.getId());

    //     mockMvc.perform(
    //             post("/api/orders")
    //                     .contentType(MediaType.APPLICATION_JSON)
    //                     .content(requestBody)
    //     )
    //     .andExpect(status().isNotFound());

    //     assertEquals(0, orderRepository.count());
    // }

}