package ru.yandex.practicum.transfer.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.transfer.dto.EventEnvelope;
import ru.yandex.practicum.transfer.exception.NotificationServiceUnavailableException;

@Component
public class NotificationClient {

    private final WebClient internalServicesWebClient;
    private final String notificationServiceUrl;

    public NotificationClient(WebClient internalServicesWebClient,
                              @Value("${app.services.notification-url}") String notificationServiceUrl) {
        this.internalServicesWebClient = internalServicesWebClient;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @CircuitBreaker(name = "notifications", fallbackMethod = "fallback")
    public void sendNotification(EventEnvelope envelope) {
        internalServicesWebClient.post()
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

