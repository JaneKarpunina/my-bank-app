package ru.yandex.practicum.accounts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.accounts.entity.BankAccount;
import ru.yandex.practicum.accounts.entity.OutboxMessage;
import ru.yandex.practicum.accounts.repository.AccountRepository;
import ru.yandex.practicum.accounts.repository.OutboxRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final OutboxRepository outboxRepository;

    public AccountService(AccountRepository accountRepository, OutboxRepository outboxRepository) {
        this.accountRepository = accountRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional(readOnly = true)
    public BankAccount getAccountByUsername(String username) {
        return accountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Аккаунт для пользователя " + username + " не найден"));
    }

    @Transactional(readOnly = true)
    public List<BankAccount> getAllOtherAccounts(String currentUsername) {
        return accountRepository.findAllByUsernameNot(currentUsername);
    }


    @Transactional
    public void updateClientInfo(String username, String newName, LocalDate newBirthDate) {
        BankAccount account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Аккаунт не найден"));

        account.setName(newName);
        account.setBirthDate(newBirthDate);
        accountRepository.save(account);

        String jsonPayload = String.format(
                "{\"username\":\"%s\", \"name\":\"%s\", \"birthDate\":\"%s\"}",
                username, newName, newBirthDate.toString()
        );

        OutboxMessage outboxMessage = new OutboxMessage();
        outboxMessage.setId(UUID.randomUUID());
        outboxMessage.setAggregateType("Account");
        outboxMessage.setAggregateId(username);
        outboxMessage.setEventType("CLIENT_INFO_UPDATED");
        outboxMessage.setPayload(jsonPayload);
        outboxMessage.setStatus("PENDING");
        outboxMessage.setAttempts(0);

        outboxRepository.save(outboxMessage);
    }

    @Transactional
    public void executeMoneyMovement(String senderUsername, String recipientUsername, int amount) {

        if (amount <= 0) {
            throw new IllegalArgumentException("Сумма перевода должна быть больше нуля");
        }

        BankAccount sender = accountRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new IllegalArgumentException("Отправитель '" + senderUsername + "' не найден"));


        BankAccount recipient = accountRepository.findByUsername(recipientUsername)
                .orElseThrow(() -> new IllegalArgumentException("Получатель '" + recipientUsername + "' не найден"));

        if (sender.getBalance() < amount) {
            throw new IllegalArgumentException("Недостаточно средств на счете пользователя " + senderUsername);
        }

        sender.setBalance(sender.getBalance() - amount);
        recipient.setBalance(recipient.getBalance() + amount);

        accountRepository.save(sender);
        accountRepository.save(recipient);
    }
}

