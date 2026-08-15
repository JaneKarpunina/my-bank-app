package ru.yandex.practicum.transfer.exception;

public class AccountsServiceUnavailableException extends RuntimeException {
    public AccountsServiceUnavailableException(Throwable cause) {
        super("Сервис аккаунтов временно недоступен. Причина: " + cause.getMessage(), cause);
    }
}
