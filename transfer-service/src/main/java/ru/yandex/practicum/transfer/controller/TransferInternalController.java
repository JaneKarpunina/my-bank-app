package ru.yandex.practicum.transfer.controller;


import lombok.extern.slf4j.Slf4j;
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
            @RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
            @RequestBody TransferRequest request
    ) {

        log.info("Получен внутренний запрос на перевод средств." +
                        " Отправитель: {}, Получатель: {}, Сумма: {}. Idempotency-Key: {}",
                request.sender(), request.recipient(), request.amount(), idempotencyKey);

        transferService.executeTransfer(idempotencyKey, request.sender(), request.recipient(), request.amount());

        log.debug("Межсервисный перевод от {} к {} (Idempotency-Key: {}) успешно обработан сервис-слоем.",
                request.sender(), idempotencyKey, request.recipient());
        return ResponseEntity.ok().build();
    }
}

