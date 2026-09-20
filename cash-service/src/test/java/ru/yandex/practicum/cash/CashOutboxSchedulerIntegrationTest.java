package ru.yandex.practicum.cash;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.cash.entity.CashOutboxMessage;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;
import ru.yandex.practicum.cash.scheduler.CashOutboxScheduler;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"${app.kafka.topics.notification:bank-notifications}"})
class CashOutboxSchedulerIntegrationTest {

    @Autowired
    private CashOutboxScheduler scheduler;

    @Autowired
    private CashOutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${app.kafka.topics.notification:bank-notifications}")
    private String topicName;

    @Test
    void shouldSendPendingCashMessagesToKafkaAndChangeStatusToProcessed() throws Exception {
        CashOutboxMessage message = new CashOutboxMessage();
        message.setId(UUID.randomUUID());
        message.setEventType("CASH_DEPOSITED");
        message.setPayload("{\"amount\": 1000, \"username\": \"ivanov\"}");
        message.setStatus("PENDING");
        message.setAttempts(0);
        outboxRepository.save(message);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("cash-test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        try (Consumer<String, String> consumer = cf.createConsumer()) {
            consumer.subscribe(Collections.singleton(topicName));

            scheduler.processCashOutboxMessages();

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, topicName, Duration.ofMillis(5000));

            Assertions.assertNotNull(record, "Сообщение Cash-Outbox не долетело до Kafka");
            Assertions.assertTrue(record.value().contains("CASH_DEPOSITED"), "Payload не содержит тип CASH_DEPOSITED");

            CashOutboxMessage updatedMessage = outboxRepository.findById(message.getId()).orElseThrow();
            Assertions.assertEquals("PROCESSED", updatedMessage.getStatus(), "Статус cash-сообщения в БД не изменился");
        }
    }
}

