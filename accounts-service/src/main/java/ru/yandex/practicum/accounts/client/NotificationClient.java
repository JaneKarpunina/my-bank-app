package ru.yandex.practicum.accounts.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.accounts.dto.EventEnvelope;
import ru.yandex.practicum.accounts.exception.NotificationServiceUnavailableException;

@Component
public class NotificationClient {

    private final WebClient webClient;
    private final String notificationServiceUrl;

    public NotificationClient(@Qualifier("notificationWebClient") WebClient webClient,
                              @Value("${app.services.notification-url}") String notificationServiceUrl) {
        this.webClient = webClient;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @CircuitBreaker(name = "notifications", fallbackMethod = "fallback")
    public void sendNotification(EventEnvelope envelope) {
        webClient.post()
                .uri(notificationServiceUrl + "/notifications/events")
                .header("Content-Type", "application/json")
                .bodyValue(envelope)
                .retrieve()
                .toBodilessEntity()
                .block();
    }


    private void fallback(String payload, Throwable exception) {
        throw new NotificationServiceUnavailableException(exception);
    }
}

