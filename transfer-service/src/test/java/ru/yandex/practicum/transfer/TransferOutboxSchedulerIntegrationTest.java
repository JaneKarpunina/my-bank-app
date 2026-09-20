package ru.yandex.practicum.transfer;


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
import ru.yandex.practicum.transfer.entity.TransferOutboxMessage;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;
import ru.yandex.practicum.transfer.scheduler.TransferOutboxScheduler;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"${app.kafka.topics.notification:bank-notifications}"})
class TransferOutboxSchedulerIntegrationTest {

    @Autowired
    private TransferOutboxScheduler scheduler;

    @Autowired
    private TransferOutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${app.kafka.topics.notification:bank-notifications}")
    private String topicName;

    @Test
    void shouldSendPendingMessagesToKafkaAndChangeStatusToProcessed() throws Exception {
        TransferOutboxMessage message = new TransferOutboxMessage();
        message.setId(UUID.randomUUID());
        message.setEventType("TRANSFER_COMPLETED");
        message.setPayload("{\"amount\": 500, \"sender\": \"ivanov\", \"recipient\": \"petrov\"}");
        message.setStatus("PENDING");
        message.setAttempts(0);
        outboxRepository.save(message);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        try (Consumer<String, String> consumer = cf.createConsumer()) {
            consumer.subscribe(Collections.singleton(topicName));

            scheduler.processTransferOutboxMessages();

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer,
                    topicName,
                    java.time.Duration.ofMillis(5000));

            Assertions.assertNotNull(record, "Сообщение не было доставлено в Kafka");
            Assertions.assertTrue(record.value().contains("TRANSFER_COMPLETED"), "Payload сообщения не содержит тип события");

            TransferOutboxMessage updatedMessage = outboxRepository.findById(message.getId()).orElseThrow();
            Assertions.assertEquals("PROCESSED", updatedMessage.getStatus(), "Статус сообщения в БД не обновился");
        }
    }
}

