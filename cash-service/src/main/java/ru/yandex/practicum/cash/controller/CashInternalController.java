package ru.yandex.practicum.cash.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.cash.dto.CashRequest;
import ru.yandex.practicum.cash.service.CashService;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/cash")
public class CashInternalController {

    private final CashService cashService;

    public CashInternalController(CashService cashService) {
        this.cashService = cashService;
    }

    @PostMapping
    public ResponseEntity<Void> processCashOperation(
            @RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
            @RequestBody CashRequest request
    ) {

        log.info("Получен внутренний запрос на проведение кассовой операции [{}]. Пользователь: {}," +
                        " Сумма: {}. Idempotency-Key: {}",
                request.action(), request.username(), request.amount(), idempotencyKey);

        cashService.executeCash(idempotencyKey, request);

        log.debug("Кассовая операция [{}] для пользователя {} (Idempotency-Key: {}) " +
                        "успешно обработана сервис-слоем.",
                request.action(), request.username(), idempotencyKey);

        return ResponseEntity.ok().build();
    }
}

