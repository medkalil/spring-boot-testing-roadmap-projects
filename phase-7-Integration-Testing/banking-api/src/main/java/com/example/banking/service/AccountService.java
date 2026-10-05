package com.example.banking.service;

import com.example.banking.dto.AccountResponse;
import com.example.banking.dto.CreateAccountRequest;
import com.example.banking.entity.Account;
import com.example.banking.entity.BankTransaction;
import com.example.banking.entity.Customer;
import com.example.banking.entity.TransactionType;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.BankTransactionRepository;
import com.example.banking.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.exception.AccountNotFoundException;
import com.example.banking.exception.CustomerNotFoundException;
import com.example.banking.exception.IllegalStateException;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    public AccountService(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            BankTransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    // Phase 7.8 — Create account
    @Transactional
    public AccountResponse createAccount(
            CreateAccountRequest request
    ) {

        Customer customer =
                customerRepository.findById(request.customerId())
                        .orElseThrow(
                                () -> new CustomerNotFoundException(
                                        "Customer not found"
                                )
                        );

        Account account = new Account(
                UUID.randomUUID().toString(),
                request.initialDeposit(),
                customer
        );

        Account saved =
                accountRepository.save(account);

        return new AccountResponse(
                saved.getId(),
                saved.getAccountNumber(),
                saved.getBalance()
        );
    }

    // Phase 7.9 — Deposit
    @Transactional
    public AccountResponse deposit(
            Long accountId,
            BigDecimal amount
    ) {

        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(
                                () -> new AccountNotFoundException(
                                        "Account not found"
                                )
                        );

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Deposit must be positive"
            );
        }

        BigDecimal newBalance =
                account.getBalance().add(amount);

        account.setBalance(newBalance);

        accountRepository.save(account);

        BankTransaction transaction =
                new BankTransaction(
                        TransactionType.DEPOSIT,
                        amount,
                        newBalance,
                        account
                );

        transactionRepository.save(transaction);

        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance()
        );
    }

    // Phase 7.10 — Withdrawal
    @Transactional
    public AccountResponse withdraw(
            Long accountId,
            BigDecimal amount
    ) {

        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(
                                () -> new AccountNotFoundException(
                                        "Account not found"
                                )
                        );

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Withdrawal must be positive"
            );
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient balance"
            );
        }

        BigDecimal newBalance =
                account.getBalance().subtract(amount);

        account.setBalance(newBalance);

        accountRepository.save(account);

        //TEST TRANSACTION REQUIREMENT:
        // Just for testing added to test tr&nsaction are rolled back: even after account update then exception happened,
        // so the account update will be roleld back and no transaction will be saved
        // to test: 
        //      1- incommenty this : throw new CustomerNotFoundException("Simulated failure");
        //      2- comment the below code this line
        //      3- comment the other test testing this part becasue we echanged the implementation by comment some parts to test transaction effect
        // throw new CustomerNotFoundException("Simulated failure"); //exp :shouldWithdraw.
        
        BankTransaction transaction =
                new BankTransaction(
                        TransactionType.WITHDRAW,
                        amount,
                        newBalance,
                        account
                );

        transactionRepository.save(transaction);

        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance()
        );
    }

    // Phase 7.11 — Get account
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {

        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(
                                () -> new AccountNotFoundException(
                                        "Account not found"
                                )
                        );

        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance()
        );
    }
}