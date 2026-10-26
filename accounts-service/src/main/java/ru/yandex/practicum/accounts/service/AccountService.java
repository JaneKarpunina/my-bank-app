package ru.yandex.practicum.accounts.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.accounts.dto.CashAction;
import ru.yandex.practicum.accounts.entity.BankAccount;
import ru.yandex.practicum.accounts.entity.IdempotencyKey;
import ru.yandex.practicum.accounts.entity.OutboxMessage;
import ru.yandex.practicum.accounts.repository.AccountRepository;
import ru.yandex.practicum.accounts.repository.IdempotencyRepository;
import ru.yandex.practicum.accounts.repository.OutboxRepository;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final OutboxRepository outboxRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final MeterRegistry meterRegistry;

    public AccountService(AccountRepository accountRepository, OutboxRepository outboxRepository,
                          IdempotencyRepository idempotencyRepository, MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.outboxRepository = outboxRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public BankAccount getAccountByUsername(String username) {
        return accountRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Попытка поиска несуществующего аккаунта для логина: '{}'", username);
                    return new RuntimeException("Accumulation account for user " + username + " not found");
                });
    }

    @Transactional(readOnly = true)
    public List<BankAccount> getAllOtherAccounts(String currentUsername) {
        return accountRepository.findAllByUsernameNot(currentUsername);
    }


    @Transactional
    public void updateClientInfo(String username, String newName, LocalDate newBirthDate, UUID idempotencyKey) {

        if (idempotencyRepository.existsById(idempotencyKey)) {
            log.debug("Сработал фильтр идемпотентности. Операция CLIENT_INFO_UPDATED " +
                    "с ключом {} уже выполнялась ранее.", idempotencyKey);
            return;
        }

        if (newName == null || newName.trim().isEmpty()) {
            log.warn("Отклонено обновление профиля {}: Передано пустое имя.", username);
            throw new IllegalArgumentException("Имя не может быть пустым");
        }

        if (newBirthDate == null) {
            log.warn("Отклонено обновление профиля {}: Дата рождения не указана.", username);
            throw new IllegalArgumentException("Дата рождения должна быть указана");
        }

        int age = Period.between(newBirthDate, LocalDate.now()).getYears();
        if (age < 18) {
            log.warn("Отклонено обновление профиля {}: Пользователь несовершеннолетний (возраст: {}).", username, age);
            throw new IllegalArgumentException("Редактирование профиля доступно только для пользователей старше 18 лет");
        }


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

        outboxRepository.save(outboxMessage);

        IdempotencyKey keyRecord = new IdempotencyKey();
        keyRecord.setId(idempotencyKey);
        keyRecord.setStatus("SUCCESS");
        idempotencyRepository.save(keyRecord);

        log.info("Профиль пользователя {} успешно обновлен. Сгенерировано Outbox-событие CLIENT_INFO_UPDATED.", username);
    }

    @Transactional
    public ResponseEntity<Void> executeMoneyMovement(String senderUsername, String recipientUsername, int amount,
                                                     UUID idempotencyKey) {

        if (idempotencyRepository.existsById(idempotencyKey)) {
            log.debug("Повторный запрос перевода. Ключ идемпотентности {} уже обработан. Возвращаем HTTP 200.", idempotencyKey);
            return ResponseEntity.ok().build();
        }


        if (amount <= 0) {
            log.warn("Отклонен перевод от {} к {}: Недопустимая сумма операции {}.", senderUsername, recipientUsername, amount);
            incrementTransferFailedMetric(senderUsername, recipientUsername);
            throw new IllegalArgumentException("Сумма перевода должна быть больше нуля");
        }

        BankAccount sender = accountRepository.findByUsername(senderUsername)
                .orElseThrow(() -> {
                    log.warn("Отклонен перевод: Отправитель '{}' не зарегистрирован в системе.", senderUsername);
                    incrementTransferFailedMetric(senderUsername, recipientUsername);
                    return new IllegalArgumentException("Отправитель '" + senderUsername + "' не найден");
                });


        BankAccount recipient = accountRepository.findByUsername(recipientUsername)
                .orElseThrow(() -> {
                    log.warn("Отклонен перевод: Получатель '{}' не зарегистрирован в системе.", recipientUsername);
                    incrementTransferFailedMetric(senderUsername, recipientUsername);
                    return new IllegalArgumentException("Получатель '" + recipientUsername + "' не найден");
                });

        if (sender.getBalance() < amount) {
            log.warn("Отклонен перевод средств из-за нехватки баланса. Отправитель: {}, Текущий баланс: {}, Запрашиваемая сумма: {}",
                    senderUsername, sender.getBalance(), amount);
            incrementTransferFailedMetric(senderUsername, recipientUsername);
            throw new IllegalArgumentException("Недостаточно средств на счете пользователя " + senderUsername);
        }

        sender.setBalance(sender.getBalance() - amount);
        recipient.setBalance(recipient.getBalance() + amount);

        accountRepository.save(sender);
        accountRepository.save(recipient);

        IdempotencyKey key = new IdempotencyKey();
        key.setId(idempotencyKey);
        key.setStatus("SUCCESS");
        idempotencyRepository.save(key);

        log.info("Транзакция перевода выполнена успешно! {} перевел {} рублей пользователю {}. Ключ идемпотентности: {}",
                senderUsername, amount, recipientUsername, idempotencyKey);

        return ResponseEntity.ok().build();
    }

    private void incrementTransferFailedMetric(String senderUsername, String recipientUsername) {
        Counter.builder("bank.transfer.failed.total")
                .description("Количество неуспешных попыток перевода денег")
                .tag("sender", senderUsername)
                .tag("recipient", recipientUsername)
                .register(meterRegistry)
                .increment();
    }

    @Transactional
    public ResponseEntity<Void>  executeCashOperation(String username, int amount, CashAction action,
                                                      UUID idempotencyKey) {

        if (idempotencyRepository.existsById(idempotencyKey)) {
            log.debug("Повторный запрос кассовой операции. Ключ {} уже зафиксирован.", idempotencyKey);
            return ResponseEntity.ok().build();
        }

        if (amount <= 0) {
            log.warn("Отклонена кассовая операция [{}]: Недопустимая сумма {}.", action, amount);
            if (action == CashAction.GET) {
                incrementBankWithdrawalFailedMetric(username);
            }
            throw new IllegalArgumentException("Сумма операции должна быть больше нуля");
        }

        BankAccount account = accountRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Отклонена кассовая операция: Пользователь '{}' не найден.", username);
                    return new IllegalArgumentException("Пользователь '" + username + "' не найден");
                });

        if (action == CashAction.PUT) {
            account.setBalance(account.getBalance() + amount);

            log.info("Баланс аккаунта {} успешно пополнен на сумму {}. Итоговый баланс: {}",
                    username, amount, account.getBalance());

        } else if (action == CashAction.GET) {
            if (account.getBalance() < amount) {
                log.warn("Атака на кассу / Подозрительная активность! Недостаточно средств для снятия." +
                                " Пользователь: '{}', Баланс: {}, Запрос: {}",
                        username, account.getBalance(), amount);
                incrementBankWithdrawalFailedMetric(username);
                throw new IllegalArgumentException("Недостаточно виртуальных средств на счете для снятия наличных");
            }

            log.info("Пользователь '{}' успешно снял {} виртуальных средств со счета. Остаток: {}",
                    username, amount, account.getBalance());
            account.setBalance(account.getBalance() - amount);
        } else {
            log.warn("Попытка выполнения неизвестного типа кассового действия: {}", action);
            throw new IllegalArgumentException("Неподдерживаемое действие: " + action);
        }

        accountRepository.save(account);

        IdempotencyKey key = new IdempotencyKey();
        key.setId(idempotencyKey);
        key.setStatus("SUCCESS");
        idempotencyRepository.save(key);

        return ResponseEntity.ok().build();
    }

    private void incrementBankWithdrawalFailedMetric(String username) {
        Counter.builder("bank.cash.withdrawal.failed.total")
                .description("Количество неуспешных попыток снятия денег")
                .tag("username", username)
                .register(meterRegistry)
                .increment();
    }
}

