package ru.yandex.practicum.transfer.dto;

public record AccountTransferDto(String sender, String recipient, int amount) {
}
