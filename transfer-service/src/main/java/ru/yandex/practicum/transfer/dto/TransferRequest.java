package ru.yandex.practicum.transfer.dto;

public record TransferRequest(
        String sender,
        String recipient,
        int amount
) {}
