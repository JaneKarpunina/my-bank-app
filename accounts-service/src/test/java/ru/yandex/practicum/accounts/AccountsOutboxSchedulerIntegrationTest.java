package ru.yandex.practicum.accounts;

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
import ru.yandex.practicum.accounts.entity.OutboxMessage; // Подставьте вашу сущность accounts-service
import ru.yandex.practicum.accounts.repository.OutboxRepository; // Подставьте ваш репозиторий
import ru.yandex.practicum.accounts.scheduler.OutboxScheduler;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=false"
})
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"${app.kafka.topics.notification:bank-notifications}"},
        controlledShutdown = true
)
class AccountsOutboxSchedulerIntegrationTest {

    @Autowired
    private OutboxScheduler scheduler;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${app.kafka.topics.notification:bank-notifications}")
    private String topicName;

    @Test
    void shouldSendPendingAccountMessagesToKafkaAndChangeStatusToProcessed() throws Exception {
        OutboxMessage message = new OutboxMessage();
        message.setId(UUID.randomUUID());
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

        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        try (Consumer<String, String> consumer = cf.createConsumer()) {
            consumer.subscribe(Collections.singleton(topicName));

            scheduler.processOutboxMessages();

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, topicName, Duration.ofMillis(5000));

            Assertions.assertNotNull(record, "Сообщение Accounts-Outbox не долетело до Kafka");
            Assertions.assertTrue(record.value().contains("CLIENT_INFO_UPDATED"), "Payload не содержит CLIENT_INFO_UPDATED");

            OutboxMessage updatedMessage = outboxRepository.findById(message.getId()).orElseThrow();
            Assertions.assertEquals("PROCESSED", updatedMessage.getStatus(), "Статус accounts-сообщения в БД не изменился");
        }
    }
}

