package ru.yandex.practicum.accounts;

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
import ru.yandex.practicum.accounts.entity.OutboxMessage;
import ru.yandex.practicum.accounts.repository.OutboxRepository;
import ru.yandex.practicum.accounts.scheduler.OutboxScheduler;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=false",
        "app.kafka.topics.notification=accounts-integration-notifications-topic"
})
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"accounts-integration-notifications-topic"},
        controlledShutdown = true
)
class AccountsOutboxSchedulerIntegrationTest {

    @Autowired
    private OutboxScheduler scheduler;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void shouldSendPendingAccountMessagesToKafkaAndChangeStatusToProcessed() {
        UUID eventId = UUID.randomUUID();

        OutboxMessage message = new OutboxMessage();
        message.setId(eventId);
        message.setEventType("CLIENT_INFO_UPDATED");
        message.setPayload("{\"username\": \"ivanov\", \"name\": \"Сергей Иванов\"}");
        message.setStatus("PENDING");
        message.setAttempts(0);
        message.setAggregateId("ivanov");
        message.setAggregateType("Account");
        outboxRepository.save(message);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("accounts-test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        try (Consumer<String, String> consumer = cf.createConsumer()) {
            String topicName = "accounts-integration-notifications-topic";
            consumer.subscribe(Collections.singleton(topicName));

            consumer.poll(Duration.ofMillis(300));

            scheduler.processOutboxMessages();

            boolean messageFound = false;
            long endTime = System.currentTimeMillis() + 5000;

            while (System.currentTimeMillis() < endTime && !messageFound) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value().contains(eventId.toString())) {
                        messageFound = true;
                        Assertions.assertTrue(record.value().contains("CLIENT_INFO_UPDATED"),
                                "Payload не содержит CLIENT_INFO_UPDATED");
                        break;
                    }
                }
            }

            Assertions.assertTrue(messageFound, "Сообщение с id " + eventId + " не найдено в изолированном топике");

            OutboxMessage updatedMessage = outboxRepository.findById(message.getId()).orElseThrow();
            Assertions.assertEquals("PROCESSED", updatedMessage.getStatus(), "Статус accounts-сообщения в БД не изменился");
        }
    }
}
