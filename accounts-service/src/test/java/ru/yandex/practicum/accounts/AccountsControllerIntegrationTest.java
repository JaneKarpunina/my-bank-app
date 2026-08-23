package ru.yandex.practicum.accounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.accounts.entity.BankAccount;
import ru.yandex.practicum.accounts.repository.AccountRepository;

import java.time.LocalDate;

@AutoConfigureMockMvc
@Transactional
class AccountsControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        BankAccount testAccount = new BankAccount();
        testAccount.setUsername("ivanov");
        testAccount.setName("Сергей Иванов");
        testAccount.setBirthDate(LocalDate.of(1990, 1, 1));
        testAccount.setBalance(5000);
        accountRepository.save(testAccount);
    }

    @Test
    void shouldIncreaseBalanceWhenActionIsPutAndRoleIsValid() throws Exception {
        String jsonRequestBody = """
                {
                    "username": "ivanov",
                    "amount": 1000,
                    "action": "PUT"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/accounts/execute-cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequestBody)
                        .header("X-Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_BALANCE_MODIFIER"))))
                .andExpect(MockMvcResultMatchers.status().isOk());

        BankAccount updatedAccount = accountRepository.findByUsername("ivanov").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(6000, updatedAccount.getBalance());
    }

    @Test
    void shouldReturnBadRequestWhenWithdrawAmountExceedsBalance() throws Exception {
        String jsonRequestBody = """
                {
                    "username": "ivanov",
                    "amount": 999999,
                    "action": "GET"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/accounts/execute-cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequestBody)
                        .header("X-Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_BALANCE_MODIFIER"))))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        BankAccount accountAfterFail = accountRepository.findByUsername("ivanov").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(5000, accountAfterFail.getBalance());
    }

    @Test
    void shouldReturnForbiddenWhenUserHasNoBalanceModifierRole() throws Exception {
        String jsonRequestBody = """
                {
                    "username": "ivanov",
                    "amount": 500,
                    "action": "PUT"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/accounts/execute-cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequestBody)
                        .header("X-Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }
}

