package ru.yandex.practicum.accounts.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.accounts.dto.AccountDto;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.AccountTransferDto;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.entity.BankAccount;
import ru.yandex.practicum.accounts.service.AccountService;

import java.util.List;
import java.util.stream.Collectors;

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

        BankAccount account = accountService.getAccountByUsername(username);

        List<BankAccount> otherAccounts = accountService.getAllOtherAccounts(username);

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
            @RequestBody UpdateAccountRequest request
    ) {

        String username = jwt.getClaimAsString("preferred_username");

        accountService.updateClientInfo(username, request.name(), request.birthdate());

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
    public ResponseEntity<Void> executeTransfer(@RequestBody AccountTransferDto dto) {
        accountService.executeMoneyMovement(dto.sender(), dto.recipient(), dto.amount());

        return ResponseEntity.ok().build();
    }
}
