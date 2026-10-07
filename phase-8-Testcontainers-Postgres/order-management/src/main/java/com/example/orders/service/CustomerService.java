package com.example.orders.service;

import com.example.orders.dto.CreateCustomerRequest;
import com.example.orders.dto.CustomerResponse;
import com.example.orders.entity.Customer;
import com.example.orders.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {

        Customer customer = new Customer(
                request.name(),
                request.email()
        );

        Customer saved = customerRepository.save(customer);

        return new CustomerResponse(
                saved.getId(),
                saved.getName(),
                saved.getEmail()
        );
    }
}