package ru.yandex.practicum.transfer;

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
import ru.yandex.practicum.transfer.entity.TransferOutboxMessage;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;
import ru.yandex.practicum.transfer.scheduler.TransferOutboxScheduler;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;


@SpringBootTest(properties = {
        "app.kafka.topics.notification=transfer-integration-notifications-topic"
})
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"transfer-integration-notifications-topic"},
        controlledShutdown = true
)
class TransferOutboxSchedulerIntegrationTest {

    @Autowired
    private TransferOutboxScheduler scheduler;

    @Autowired
    private TransferOutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void shouldSendPendingMessagesToKafkaAndChangeStatusToProcessed() {
        UUID eventId = UUID.randomUUID();

        TransferOutboxMessage message = new TransferOutboxMessage();
        message.setId(eventId);
        message.setEventType("TRANSFER_COMPLETED");
        message.setPayload("{\"amount\": 500, \"sender\": \"ivanov\", \"recipient\": \"petrov\"}");
        message.setStatus("PENDING");
        message.setAttempts(0);

        outboxRepository.save(message);

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("transfer-test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        try (Consumer<String, String> consumer = cf.createConsumer()) {
            String topicName = "transfer-integration-notifications-topic";
            consumer.subscribe(Collections.singleton(topicName));

            consumer.poll(Duration.ofMillis(300));

            scheduler.processTransferOutboxMessages();

            boolean messageFound = false;
            long endTime = System.currentTimeMillis() + 5000;

            while (System.currentTimeMillis() < endTime && !messageFound) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value().contains(eventId.toString())) {
                        messageFound = true;
                        Assertions.assertTrue(record.value().contains("TRANSFER_COMPLETED"));
                        break;
                    }
                }
            }

            Assertions.assertTrue(messageFound, "Сообщение не найдено в изолированном топике");

            TransferOutboxMessage updatedMessage = outboxRepository.findById(message.getId()).orElseThrow();
            Assertions.assertEquals("PROCESSED", updatedMessage.getStatus());
        }
    }
}
