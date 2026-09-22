package ru.yandex.practicum.cash;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(properties = {
        "app.kafka.topics.notification=cash-integration-notifications-topic"
})
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"cash-integration-notifications-topic"},
        controlledShutdown = true
)
class CashOutboxSchedulerIntegrationTest {

    @Autowired
    private CashOutboxScheduler scheduler;

    @Autowired
    private CashOutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void shouldSendPendingCashMessagesToKafkaAndChangeStatusToProcessed() {
        UUID eventId = UUID.randomUUID();

        CashOutboxMessage message = new CashOutboxMessage();
        message.setId(eventId);
        message.setEventType("CASH_DEPOSITED");
        message.setPayload("{\"amount\": 1000, \"username\": \"ivanov\"}");
        message.setStatus("PENDING");
        message.setAttempts(0);

        outboxRepository.save(message);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("cash-test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        try (Consumer<String, String> consumer = cf.createConsumer()) {

            String topicName = "cash-integration-notifications-topic";
            consumer.subscribe(Collections.singleton(topicName));

            consumer.poll(Duration.ofMillis(300));

            scheduler.processCashOutboxMessages();

            boolean messageFound = false;
            long endTime = System.currentTimeMillis() + 5000; // таймаут 5 секунд

            while (System.currentTimeMillis() < endTime && !messageFound) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value().contains(eventId.toString())) {
                        messageFound = true;
                        Assertions.assertTrue(record.value().contains("CASH_DEPOSITED"),
                                "Payload не содержит тип CASH_DEPOSITED");
                        break;
                    }
                }
            }

            Assertions.assertTrue(messageFound,
                    "Сообщение с id " + eventId + " не найдено в изолированном топике кассы");

            CashOutboxMessage updatedMessage = outboxRepository.findById(message.getId()).orElseThrow();
            Assertions.assertEquals("PROCESSED",
                    updatedMessage.getStatus(), "Статус cash-сообщения в БД не изменился");
        }
    }
}
