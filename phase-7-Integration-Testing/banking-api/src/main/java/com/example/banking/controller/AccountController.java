package com.example.banking.controller;

import com.example.banking.dto.AccountResponse;
import com.example.banking.dto.AmountRequest;
import com.example.banking.dto.CreateAccountRequest;
import com.example.banking.service.AccountService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(
            AccountService accountService
    ) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(
            @RequestBody CreateAccountRequest request
    ) {
        return accountService.createAccount(request);
    }

    @PostMapping("/{id}/deposit")
    public AccountResponse deposit(
            @PathVariable Long id,
            @RequestBody AmountRequest request
    ) {
        return accountService.deposit(
                id,
                request.amount()
        );
    }

    @PostMapping("/{id}/withdraw")
    public AccountResponse withdraw(
            @PathVariable Long id,
            @RequestBody AmountRequest request
    ) {
        return accountService.withdraw(
                id,
                request.amount()
        );
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(
            @PathVariable Long id
    ) {
        return accountService.getAccount(id);
    }
}