package ru.yandex.practicum.cash.dto;

public record CashRequest(
        String username,
        int amount,
        CashAction action
) {}
