package com.example.banking.repository;

import com.example.banking.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, Long> {
}