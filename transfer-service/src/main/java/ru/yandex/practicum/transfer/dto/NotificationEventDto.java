package ru.yandex.practicum.transfer.dto;

public record NotificationEventDto(String recipient, int amount, String sender) {
}
