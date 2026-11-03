package ru.yandex.practicum.cash.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
            @RequestBody CashRequest request
    ) {

        log.info("Получен внутренний запрос на проведение кассовой операции [{}]. Пользователь: {}," +
                        " Сумма: {}. Idempotency-Key: {}",
                request.action(), request.username(), request.amount(), idempotencyKey);

        String authenticatedUser = jwt.getClaimAsString("preferred_username");

        if (!authenticatedUser.equals(request.username())) {
            log.warn("Нарушение безопасности! Аутентифицированный пользователь '{}' " +
                            "попытался провести операцию по чужому счету '{}'! Ключ: {}",
                    authenticatedUser, request.username(), idempotencyKey);

            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        cashService.executeCash(idempotencyKey, request);

        log.debug("Кассовая операция [{}] для пользователя {} (Idempotency-Key: {}) " +
                        "успешно обработана сервис-слоем.",
                request.action(), request.username(), idempotencyKey);

        return ResponseEntity.ok().build();
    }
}

