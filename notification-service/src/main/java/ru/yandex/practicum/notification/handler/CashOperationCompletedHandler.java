package ru.yandex.practicum.notification.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.notification.dto.CashAction;
import ru.yandex.practicum.notification.dto.EventEnvelope;


@Component
@Slf4j
public class CashOperationCompletedHandler implements EventHandler {

    private final ObjectMapper objectMapper;

    public CashOperationCompletedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSupportedEventType() {
        return "CASH_OPERATION_COMPLETED";
    }

    @Override
    public void handle(EventEnvelope envelope) throws Exception {
        CashPayload p = objectMapper.readValue(envelope.payload(), CashPayload.class);

        String operationText = (p.action() == CashAction.PUT) ? "пополнение счёта на" : "снятие наличных на сумму";

        log.info("Пользователь '{}' успешно выполнил {}: {}.",
                p.username(), operationText, p.amount());
    }
}

record CashPayload(String username,
                   int amount,
                   CashAction action) {}
