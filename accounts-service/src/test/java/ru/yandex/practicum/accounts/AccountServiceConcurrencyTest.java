package ru.yandex.practicum.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.accounts.dto.CashAction;
import ru.yandex.practicum.accounts.entity.BankAccount;
import ru.yandex.practicum.accounts.repository.AccountRepository;
import ru.yandex.practicum.accounts.service.AccountService;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class AccountServiceConcurrencyTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountRepository accountRepository;

    @Test
    public void testConcurrentCashOperations_ShouldBalanceCorrectly() throws Exception {
        BankAccount account = new BankAccount();
        account.setUsername("concurrency_user");
        account.setName("name");
        account.setBirthDate(LocalDate.now());
        account.setBalance(10000);
        accountRepository.save(account);

        int threadsCount = 10;
        CountDownLatch latch = new CountDownLatch(threadsCount);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadsCount)) {

            for (int i = 0; i < threadsCount; i++) {
                executor.submit(() -> {
                    try {
                        accountService.executeCashOperation("concurrency_user", 500,
                                CashAction.GET, UUID.randomUUID());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        }

        BankAccount finalAccount = accountRepository.findByUsername("concurrency_user").get();
        assertEquals(5000, finalAccount.getBalance());
    }

    @Test
    public void testConcurrentMoneyMovements_ShouldMaintainTotalSum() throws Exception {
        BankAccount userA = new BankAccount();
        userA.setUsername("user_a");
        userA.setName("name");
        userA.setBirthDate(LocalDate.now());
        userA.setBalance(10000);
        accountRepository.save(userA);

        BankAccount userB = new BankAccount();
        userB.setUsername("user_b");
        userB.setName("name");
        userB.setBirthDate(LocalDate.now());
        userB.setBalance(10000);
        accountRepository.save(userB);

        int threadsCount = 10;
        CountDownLatch latch = new CountDownLatch(threadsCount);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadsCount)) {

            for (int i = 0; i < threadsCount; i++) {
                executor.submit(() -> {
                    try {
                        accountService.executeMoneyMovement("user_a", "user_b",
                                100, UUID.randomUUID());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        }

        BankAccount finalA = accountRepository.findByUsername("user_a").get();
        BankAccount finalB = accountRepository.findByUsername("user_b").get();

        assertEquals(9000, finalA.getBalance());
        assertEquals(11000, finalB.getBalance());
        assertEquals(20000, finalA.getBalance() + finalB.getBalance());
    }


}

