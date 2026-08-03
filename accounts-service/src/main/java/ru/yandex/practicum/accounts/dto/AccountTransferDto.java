package ru.yandex.practicum.accounts.dto;

public record AccountTransferDto(
        String sender,
        String recipient,
        int amount
) {}
