package com.example.banking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.example.banking.entity.Account;
import com.example.banking.entity.BankTransaction;
import com.example.banking.entity.Customer;
import com.example.banking.entity.TransactionType;
import com.example.banking.repository.AccountRepository;
import com.example.banking.repository.BankTransactionRepository;
import com.example.banking.repository.CustomerRepository;

import jakarta.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import java.math.BigDecimal;

// Having this: @AutoConfigureMockMvc  + private MockMvc mockMvc:
// mockMvc.perform(...)
// does not call your controller directly.
// It goes through Spring MVC.
// Conceptually:
//    MockMvc
//    ↓
//    HTTP request simulation
//    ↓
//    Spring MVC
//    ↓
//    Controller
//    ↓
//    Service
//    ↓
//    Repository
//    ↓
//    Hibernate
//    ↓
//    H2
// This is the key Phase 7 experience.

// We use @Transactional to rollback the transaction after each test. (because SpringBootTest doesn't do it automatically rollback after each test).
// or we can have : 
// @BeforeEach
// void cleanDatabase() {
//     accountRepository.deleteAll();
//     customerRepository.deleteAll();
// }

@SpringBootTest 
@AutoConfigureMockMvc 
@Transactional 
class BankingApplicationTest {

    @Autowired 
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired 
    private BankTransactionRepository bankTransactionRepository;

    @Test
    void shouldCreateAccount() throws Exception {

        Customer customer =
                customerRepository.save(
                        new Customer(
                                "John Doe",
                                "john@example.com"
                        )
                );

        String requestBody = """
            {
                "customerId": %d,
                "initialDeposit": 500
            }
            """.formatted(customer.getId());

        mockMvc.perform(
                post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.balance")
                .value(500));

        Optional<Account> account = accountRepository.findAll().stream().findFirst();
        
        assertEquals(500, account.get().getBalance().intValue());
        assertEquals(customer.getId(), account.get().getCustomer().getId());
    }

    @Test
    void shouldGetAccount() throws Exception {
        Customer customer = customerRepository.save(new Customer("John Doe", "john@example.com"));
        Account account = accountRepository.save(new Account(UUID.randomUUID().toString(), BigDecimal.valueOf(1000), customer));

        mockMvc.perform(
            get("/accounts/{id}", account.getId())
                    .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.balance")
                .value(1000))
        .andExpect(jsonPath("$.accountNumber")
                .value(account.getAccountNumber()));
    }

    @Test
    void shouldDeposit() throws Exception {

        Customer customer = customerRepository.save(new Customer("John Doe", "john@example.com"));
        Account account = accountRepository.save(new Account(UUID.randomUUID().toString(), BigDecimal.valueOf(1000), customer));

        String requestBody = """
            {
                "amount": 500
            }
            """;

        mockMvc.perform(
                post("/accounts/{id}/deposit", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        )
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.balance")
                .value(1500));

        Optional<Account> existingAccount = accountRepository.findById(account.getId());
        assertEquals(1500, existingAccount.get().getBalance().intValue());
        assertEquals(customer.getId(), existingAccount.get().getCustomer().getId());

        Optional<BankTransaction> transaction = bankTransactionRepository.findAll().stream().findFirst();
        assertTrue(transaction.isPresent());
        assertEquals(account.getId(), transaction.get().getAccount().getId());
        assertEquals(500, transaction.get().getAmount().intValue());
        assertEquals(1500, transaction.get().getBalanceAfter().intValue());
        assertEquals(TransactionType.DEPOSIT, transaction.get().getType());
    }

    @Test
    void shouldWithdraw() throws Exception {

        Customer customer = customerRepository.save(new Customer("John Doe", "john@example.com"));
        Account account = accountRepository.save(new Account(UUID.randomUUID().toString(), BigDecimal.valueOf(1000), customer));

        String requestBody = """
            {
                "amount": 500
            }
            """;

        mockMvc.perform(
                post("/accounts/{id}/withdraw", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        )
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.balance")
                .value(500));

        Optional<Account> existingAccount = accountRepository.findById(account.getId());
        assertEquals(500, existingAccount.get().getBalance().intValue());
        assertEquals(customer.getId(), existingAccount.get().getCustomer().getId());

        Optional<BankTransaction> transaction = bankTransactionRepository.findAll().stream().findFirst();
        assertTrue(transaction.isPresent());
        assertEquals(account.getId(), transaction.get().getAccount().getId());
        assertEquals(500, transaction.get().getAmount().intValue());
        assertEquals(500, transaction.get().getBalanceAfter().intValue());
        assertEquals(TransactionType.WITHDRAW, transaction.get().getType());
    }

    // InsufficentBalance : balance 1000, withdraw 1500
    @Test
    void shouldThrowInsufficentBalanceExceptionWhenWithdrawingMoreThanBalance() throws Exception {

        Customer customer = customerRepository.save(new Customer("John Doe", "john@example.com"));
        Account account = accountRepository.save(new Account(UUID.randomUUID().toString(), BigDecimal.valueOf(1000), customer));

        String requestBody = """
            {
                "amount": 1500
            }
            """;

        mockMvc.perform(
                post("/accounts/{id}/withdraw", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message")
                .value("Insufficient balance"));

        Optional<Account> existingAccount = accountRepository.findById(account.getId());
        assertEquals(1000, existingAccount.get().getBalance().intValue());

        Optional<BankTransaction> transaction = bankTransactionRepository.findAll().stream().findFirst();
        assertFalse(transaction.isPresent());
    }

    // invalid ammount (deposit or withdraw are the same)

    @Test
    void shouldThrowInvalidAmountExceptionWhenDepositingZero() throws Exception {

        Customer customer = customerRepository.save(new Customer("John Doe", "john@example.com"));
        Account account = accountRepository.save(new Account(UUID.randomUUID().toString(), BigDecimal.valueOf(1000), customer));

        String requestBody = """
            {
                "amount": -100
            }
            """;

        mockMvc.perform(
                post("/accounts/{id}/deposit", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message")
                .value("Deposit must be positive"));

        Optional<Account> existingAccount = accountRepository.findById(account.getId());
        assertEquals(1000, existingAccount.get().getBalance().intValue());

        Optional<BankTransaction> transaction = bankTransactionRepository.findAll().stream().findFirst();
        assertFalse(transaction.isPresent());
    }

    // account not found

    // @Test
    // void shouldThrowAccountNotFoundExceptionWhenGettingNonExistingAccount() throws Exception {
    //     mockMvc.perform(
    //         get("/accounts/{id}", 1L)
    //                 .contentType(MediaType.APPLICATION_JSON)
    //     )
    //     .andExpect(status().isNotFound())
    //     .andExpect(jsonPath("$.message")
    //             .value("Account not found"));

    //     // testing transactions here to vaidate that every test is isolated and rolledback after it finich (and the db in this test is empty)
    //     List<BankTransaction> transactions = bankTransactionRepository.findAll();
    //     assertEquals(0, transactions.size());
    //     assertTrue(transactions.isEmpty());
    // }

    // simulate transaction not saved after exception happened: exp will be in the withdraw
    // To test this:
    // 1. uncomment this test
    // 2. follow the TEST TRANSACTION REQUIREMENT in the AccountService.java
    // 3- comment the above @Transactional: because it will make it transaction (service) inside a test transaction.

    // @Test
    // void shouldThrowExceptionWhenWithdrawingMoreThanBalanceAndTransactionIsNotSaved() throws Exception {

    //     Customer customer = customerRepository.save(new Customer("John Doe", "john@example.com"));
    //     Account account = accountRepository.save(new Account(UUID.randomUUID().toString(), BigDecimal.valueOf(1000), customer));

    //     String requestBody = """
    //         {
    //             "amount": 500
    //         }
    //         """;

    //     mockMvc.perform(
    //             post("/accounts/{id}/withdraw", account.getId())
    //                     .contentType(MediaType.APPLICATION_JSON)
    //                     .content(requestBody)
    //     )
    //     .andExpect(status().isNotFound());


    //     Optional<Account> existingAccount = accountRepository.findById(account.getId());
    //     assertEquals(1000, existingAccount.get().getBalance().intValue());

    //     Optional<BankTransaction> transaction = bankTransactionRepository.findAll().stream().findFirst();
    //     assertFalse(transaction.isPresent());
    // }


    
    // Testing transaction creation
    @Test
    void shouldCreateTransactionWhenWithdrawing() throws Exception
    {
        // setup
        Customer customer = customerRepository.save(new Customer("John Doe", "john@example.com"));
        Account account = accountRepository.save(new Account(UUID.randomUUID().toString(), BigDecimal.valueOf(1000), customer));

        String requestBody = """
            {
                "amount": 500
            }
            """;

        mockMvc.perform(
                post("/accounts/{id}/withdraw", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        )
        .andExpect(status().is2xxSuccessful());

        // assert
        Optional<Account> existingAccount = accountRepository.findById(account.getId());
        assertEquals(500, existingAccount.get().getBalance().intValue());
        assertEquals(customer.getId(), existingAccount.get().getCustomer().getId());

        Optional<BankTransaction> transaction = bankTransactionRepository.findAll().stream().findFirst();
        assertTrue(transaction.isPresent());
        assertEquals(account.getId(), transaction.get().getAccount().getId());
        assertEquals(500, transaction.get().getAmount().intValue());
        assertEquals(500, transaction.get().getBalanceAfter().intValue());
        assertEquals(TransactionType.WITHDRAW, transaction.get().getType());
    }


    // multiple requests test
    @Test
    void shouldHandleCompleteAccountFlow() throws Exception {

        Customer customer = customerRepository.save(new Customer("John", "john@example.com"));
        Account account = accountRepository.save(new Account(UUID.randomUUID().toString(), BigDecimal.valueOf(1000), customer));

        mockMvc.perform(
                post("/accounts/{id}/deposit", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": 500
                            }
                            """)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance")
                .value(1500));

        mockMvc.perform(
                post("/accounts/{id}/withdraw", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": 300
                            }
                            """)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance")
                .value(1200));

        mockMvc.perform(
                get("/accounts/{id}", account.getId())
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance")
                .value(1200));

        List<BankTransaction> transactions = bankTransactionRepository.findAll();
        assertEquals(2, transactions.size());
        assertEquals(TransactionType.DEPOSIT, transactions.get(0).getType());
        assertEquals(TransactionType.WITHDRAW, transactions.get(1).getType());

        Account accountExisted = accountRepository.findById(account.getId()).get();
        assertEquals(1200, accountExisted.getBalance().intValue());
    }

    // Test validation 
    /*
     * to Test this:
     *  1. uncomment this test
     *  2. comment all the test above
     *  3. add MethodArgumentNotValidException in the global advice exception
     *  4. add validation in the pom.xml
     *  5. add the validation to exp: AmountRequest: (@NotNull @DecimalMin("0.01") BigDecimal amount)
     *  6. add @Valid to the deposit request body
     *   
     */
    // @Test
    // void shouldThrowExceptionWhenValidationFail() throws Exception {

    //     Customer customer = customerRepository.save(new Customer("John", "john@example.com"));
    //     Account account = accountRepository.save(new Account(UUID.randomUUID().toString(), BigDecimal.valueOf(1000), customer));

    //     String requestBody = """
    //         {
    //             "amount": -100
    //         }
    //         """;

    //     mockMvc.perform(
    //             post("/accounts/{id}/deposit", account.getId())
    //                     .contentType(MediaType.APPLICATION_JSON)
    //                     .content(requestBody)
    //     )
    //     .andExpect(status().isBadRequest());
    // }


}
