package com.example.banking.dto;

import java.math.BigDecimal;

public record CreateAccountRequest(
        Long customerId,
        BigDecimal initialDeposit
) {
}