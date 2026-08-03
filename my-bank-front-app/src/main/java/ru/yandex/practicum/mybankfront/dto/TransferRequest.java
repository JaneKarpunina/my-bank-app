package ru.yandex.practicum.mybankfront.dto;

public record TransferRequest(
        String sender,
        String recipient,
        int amount
) {}

