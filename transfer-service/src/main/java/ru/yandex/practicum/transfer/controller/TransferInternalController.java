package ru.yandex.practicum.transfer.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/transfers")
public class TransferInternalController {

    private final TransferService transferService;

    public TransferInternalController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<Void> initiateTransfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
            @RequestBody TransferRequest request
    ) {

        log.info("Получен внутренний запрос на перевод средств." +
                        " Отправитель: {}, Получатель: {}, Сумма: {}. Idempotency-Key: {}",
                request.sender(), request.recipient(), request.amount(), idempotencyKey);

        String authenticatedUser = jwt.getClaimAsString("preferred_username");

        if (!authenticatedUser.equals(request.sender())) {
            log.warn("Нарушение безопасности! Пользователь '{}' попытался отправить " +
                    "денежный перевод со счета чужого пользователя '{}'!", authenticatedUser, request.sender());

            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        transferService.executeTransfer(idempotencyKey, request.sender(), request.recipient(), request.amount());

        log.debug("Межсервисный перевод от {} к {} (Idempotency-Key: {}) успешно обработан сервис-слоем.",
                request.sender(), idempotencyKey, request.recipient());
        return ResponseEntity.ok().build();
    }
}

