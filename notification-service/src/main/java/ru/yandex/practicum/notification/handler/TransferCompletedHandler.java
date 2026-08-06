package ru.yandex.practicum.notification.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.notification.dto.EventEnvelope;

@Component
@Slf4j
public class TransferCompletedHandler implements EventHandler {

    private final ObjectMapper objectMapper;

    public TransferCompletedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSupportedEventType() {
        return "TRANSFER_COMPLETED";
    }

    @Override
    public void handle(EventEnvelope envelope) throws Exception {
        TransferPayload p = objectMapper.readValue(envelope.payload(), TransferPayload.class);
        log.info("=> Пользователь {} получил перевод {} от пользователя {}",
                p.recipient(), p.amount(), p.sender());
    }
}

record TransferPayload(String recipient, int amount, String sender) {}
