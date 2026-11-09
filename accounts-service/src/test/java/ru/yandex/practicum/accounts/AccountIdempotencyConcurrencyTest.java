package ru.yandex.practicum.accounts;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import ru.yandex.practicum.accounts.entity.BankAccount;
import ru.yandex.practicum.accounts.repository.AccountRepository;
import ru.yandex.practicum.accounts.repository.IdempotencyRepository;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class AccountIdempotencyConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    private final String testUser = "concurrency_idempotency_user";

    @BeforeEach
    public void setUp() {
        idempotencyRepository.deleteAll();
        accountRepository.deleteAll();

        BankAccount account = new BankAccount();
        account.setUsername(testUser);
        account.setBalance(10000);
        account.setName("name");
        account.setBirthDate(LocalDate.now());
        accountRepository.save(account);
    }

    @Test
    public void testStrictParallelIdempotency_WithSameUUID_ShouldHandleConflictAndPreventDoubleSpend() throws Exception {
        UUID sharedIdempotencyKey = UUID.randomUUID();
        int amountToWithdraw = 3000;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);

            class CashTask implements java.util.concurrent.Callable<MvcResult> {
                @Override
                public MvcResult call() throws Exception {
                    readyLatch.countDown();
                    startLatch.await();

                    return mockMvc.perform(post("/accounts/execute-cash")
                                    .header("X-Idempotency-Key", sharedIdempotencyKey.toString())
                                    .with(jwt()
                                            .authorities(new SimpleGrantedAuthority("ROLE_BALANCE_MODIFIER"))
                                            .jwt(jwtBuilder -> jwtBuilder.claim("preferred_username", testUser))
                                    )
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(String.format("""
                                            {
                                               "username": "%s",
                                               "amount": %d,
                                               "action": "GET"
                                            }
                                            """, testUser, amountToWithdraw)))
                            .andReturn();
                }
            }

            Future<MvcResult> future1 = executor.submit(new CashTask());
            Future<MvcResult> future2 = executor.submit(new CashTask());

            readyLatch.await();
            startLatch.countDown();

            MvcResult result1 = future1.get();
            MvcResult result2 = future2.get();

            int status1 = result1.getResponse().getStatus();
            int status2 = result2.getResponse().getStatus();

            boolean hasSuccess = status1 == 200 && status2 == 200;

            Assertions.assertTrue(hasSuccess, "Оба потока должны вернуть статус 200 OK");
        }


        BankAccount finalAccount = accountRepository.findByUsername(testUser)
                .orElseThrow(() -> new AssertionError("Пользователь не найден в БД"));

        Assertions.assertEquals(7000, finalAccount.getBalance(),
                "Баланс некорректен! Произошло двойное списание или баланс не обновился вовсе.");
    }

    @Test
    public void testStrictParallelTransferIdempotency_WithSameUUID_ShouldPreventDoubleSpend() throws Exception {
        String senderUser = "transfer_sender_user";
        String recipientUser = "transfer_recipient_user";
        UUID sharedIdempotencyKey = UUID.randomUUID();
        int transferAmount = 2000;

        BankAccount sender = new BankAccount();
        sender.setUsername(senderUser);
        sender.setBalance(10000);
        sender.setName("Sender");
        sender.setBirthDate(LocalDate.now());
        accountRepository.save(sender);

        BankAccount recipient = new BankAccount();
        recipient.setUsername(recipientUser);
        recipient.setBalance(5000);
        recipient.setName("Recipient");
        recipient.setBirthDate(LocalDate.now());
        accountRepository.save(recipient);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);

            class TransferTask implements java.util.concurrent.Callable<MvcResult> {
                @Override
                public MvcResult call() throws Exception {
                    readyLatch.countDown();
                    startLatch.await();

                    return mockMvc.perform(post("/accounts/execute-transfer")
                                    .header("X-Idempotency-Key", sharedIdempotencyKey.toString())
                                    .with(jwt()
                                            .authorities(new SimpleGrantedAuthority("ROLE_BALANCE_MODIFIER"))
                                            .jwt(jwtBuilder ->
                                                    jwtBuilder.claim("preferred_username", senderUser))
                                    )
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(String.format("""
                                            {
                                               "sender": "%s",
                                               "recipient": "%s",
                                               "amount": %d
                                            }
                                            """, senderUser, recipientUser, transferAmount)))
                            .andReturn();
                }
            }

            Future<MvcResult> future1 = executor.submit(new TransferTask());
            Future<MvcResult> future2 = executor.submit(new TransferTask());

            readyLatch.await();
            startLatch.countDown();

            MvcResult result1 = future1.get();
            MvcResult result2 = future2.get();

            int status1 = result1.getResponse().getStatus();
            int status2 = result2.getResponse().getStatus();

            boolean isSuccess = status1 == 200 && status2 == 200;

            Assertions.assertTrue(isSuccess, "Оба потока должны вернуть статус 200 OK");
        }

        BankAccount finalSender = accountRepository.findByUsername(senderUser).orElseThrow();
        Assertions.assertEquals(8000, finalSender.getBalance(),
                "Критическая ошибка! У отправителя произошло двойное списание денег.");

        BankAccount finalRecipient = accountRepository.findByUsername(recipientUser).orElseThrow();
        Assertions.assertEquals(7000, finalRecipient.getBalance(),
                "Критическая ошибка! У получателя произошло двойное зачисление денег.");
    }

    @Test
    public void testUserIvanov_ShouldReturn403Forbidden_OnExecuteCash() throws Exception {
        UUID key = UUID.randomUUID();

        mockMvc.perform(post("/accounts/execute-cash")
                        .header("X-Idempotency-Key", key.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwtBuilder -> jwtBuilder.claim("preferred_username", "ivanov"))
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                   "username": "ivanov",
                                   "amount": 500,
                                   "action": "GET"
                                }
                                """))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    public void testServiceCash_ShouldReturn200Ok_OnExecuteCash() throws Exception {
        UUID key = UUID.randomUUID();
        String cashServiceUser = "cash-service-pod";

        BankAccount account = new BankAccount();
        account.setUsername(cashServiceUser);
        account.setBalance(5000);
        account.setName("Cash Service Acc");
        account.setBirthDate(LocalDate.now());
        accountRepository.save(account);

        mockMvc.perform(post("/accounts/execute-cash")
                        .header("X-Idempotency-Key", key.toString())
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_BALANCE_MODIFIER"))
                                .jwt(jwtBuilder -> jwtBuilder.claim("preferred_username", "cash-service"))
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                   "username": "%s",
                                   "amount": 1000,
                                   "action": "GET"
                                }
                                """, cashServiceUser)))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }


}

