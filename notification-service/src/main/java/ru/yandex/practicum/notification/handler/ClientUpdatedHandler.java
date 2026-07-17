package ru.yandex.practicum.notification.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.notification.dto.EventEnvelope;

import java.time.LocalDate;

@Component
@Slf4j
public class ClientUpdatedHandler implements EventHandler {
    private final ObjectMapper objectMapper;

    public ClientUpdatedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSupportedEventType() {
        return "CLIENT_INFO_UPDATED";
    }

    @Override
    public void handle(EventEnvelope envelope) throws Exception {
        ClientUpdatedPayload p = objectMapper.readValue(envelope.payload(), ClientUpdatedPayload.class);
        log.info("=> Пользователь {} обновил профиль. Новое имя: {}, новая дата рождения: {}",
                p.username(), p.name(), p.birthDate());
    }
}

record ClientUpdatedPayload(String username, String name, LocalDate birthDate) {}
