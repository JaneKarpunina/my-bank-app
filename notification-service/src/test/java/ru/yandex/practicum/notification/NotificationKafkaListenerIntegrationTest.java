package ru.yandex.practicum.notification;


import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.notification.handler.TransferCompletedHandler;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "app.kafka.topics.notification=notification-integration-test-topic"
})
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"notification-integration-test-topic"},
        controlledShutdown = true
)
class NotificationKafkaListenerIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @SpyBean
    private TransferCompletedHandler mockHandler;

    @Test
    void shouldReceiveMessageFromKafkaAndInvokeSupportedHandler() throws Exception {

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(pf);

        String jsonPayload = "{\"eventId\":\"" + java.util.UUID.randomUUID() + "\",\"eventType\":\"TRANSFER_COMPLETED\",\"aggregateType\":\"TRANSFER\",\"payload\":\"{}\",\"timestamp\":\"2026-09-19T12:00:00Z\"}";

        String topicName = "notification-integration-test-topic";
        template.send(topicName, jsonPayload).get();

        verify(mockHandler, timeout(5000).atLeastOnce()).handle(any());
    }
}

