package ru.yandex.practicum.transfer.controller;


import ru.yandex.practicum.transfer.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

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
            @RequestParam("sender") String sender,
            @RequestParam("recipient") String recipient,
            @RequestParam("amount") int amount
    ) {
        transferService.executeTransfer(idempotencyKey, sender, recipient, amount);
        return ResponseEntity.ok().build();
    }
}

