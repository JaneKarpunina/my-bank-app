package ru.yandex.practicum.cash.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.cash.dto.CashRequest;
import ru.yandex.practicum.cash.service.CashService;
import java.util.UUID;

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
        cashService.executeCash(idempotencyKey, request);

        return ResponseEntity.ok().build();
    }
}

