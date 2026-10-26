package ru.yandex.practicum.accounts.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.accounts.dto.*;
import ru.yandex.practicum.accounts.entity.BankAccount;
import ru.yandex.practicum.accounts.service.AccountService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/accounts")
public class AccountsController {

    private final AccountService accountService;

    public AccountsController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<AccountResponse> getAccount(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");

        log.info("Получен запрос на получение данных аккаунта для пользователя: {}", username);

        BankAccount account = accountService.getAccountByUsername(username);

        List<BankAccount> otherAccounts = accountService.getAllOtherAccounts(username);

        log.debug("Для пользователя {} найдено {} других аккаунтов в системе", username, otherAccounts.size());

        List<AccountDto> otherUsersDtoList = otherAccounts.stream()
                .map(acc -> new AccountDto(
                        acc.getUsername(),
                        acc.getName()
                ))
                .toList();

        AccountResponse response = new AccountResponse();
        response.setName(account.getName());
        response.setBirthdate(account.getBirthDate());
        response.setSum(account.getBalance());
        response.setAccounts(otherUsersDtoList);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> updateAccount(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
            @RequestBody UpdateAccountRequest request
    ) {

        String username = jwt.getClaimAsString("preferred_username");

        log.info("Получен запрос на обновление профиля пользователя: {}. Idempotency-Key: {}", username, idempotencyKey);

        accountService.updateClientInfo(username, request.name(), request.birthdate(), idempotencyKey);

        BankAccount updatedAccount = accountService.getAccountByUsername(username);
        List<BankAccount> otherAccounts = accountService.getAllOtherAccounts(username);

        List<AccountDto> otherUsersDtoList = otherAccounts.stream()
                .map(acc -> new AccountDto(
                        acc.getUsername(),
                        acc.getName()
                ))
                .collect(Collectors.toList());

        AccountResponse response = new AccountResponse();
        response.setName(updatedAccount.getName());
        response.setBirthdate(updatedAccount.getBirthDate());
        response.setSum(updatedAccount.getBalance());
        response.setAccounts(otherUsersDtoList);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute-transfer")
    public ResponseEntity<Void> executeTransfer(@RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
                                                @RequestBody AccountTransferDto dto) {

        log.info("Инициация перевода средств. Отправитель: {}, Получатель: {}, Сумма: {}. Idempotency-Key: {}",
                dto.sender(), dto.recipient(), dto.amount(), idempotencyKey);
        ResponseEntity<Void> response = accountService.executeMoneyMovement(dto.sender(), dto.recipient(),
                dto.amount(), idempotencyKey);

        log.debug("Результат перевода средств (Idempotency-Key: {}): Статус сэрвис-слоя {}",
                idempotencyKey, response.getStatusCode());
        return response;
    }

    @PostMapping("/execute-cash")
    public ResponseEntity<Void> executeCash(@RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
                                            @RequestBody AccountOperationDto dto) {
        log.info("Инициация кассовой операции [{}]. Пользователь: {}, Сумма: {}. Idempotency-Key: {}",
                dto.action(), dto.username(), dto.amount(), idempotencyKey);

        ResponseEntity<Void> response = accountService.executeCashOperation(dto.username(), dto.amount(),
                dto.action(), idempotencyKey);

        log.debug("Результат кассовой операции [{}] (Idempotency-Key: {}): Статус {}", dto.action(),
                idempotencyKey, response.getStatusCode());
        return response;
    }
}
