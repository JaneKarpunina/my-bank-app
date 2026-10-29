package ru.yandex.practicum.transfer.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaProducerConfig {

    @Value("${app.kafka.topics.notification:bank-notifications}")
    private String notificationTopic;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.acks:all}")
    private String acks;

    @Value("${spring.kafka.producer.retries:5}")
    private int retries;

    @Value("${spring.kafka.producer.properties.retry.backoff.ms:100}")
    private int retryBackoffMs;

    @Value("${spring.kafka.producer.key-serializer:org.apache.kafka.common.serialization.StringSerializer}")
    private String keySerializer;

    @Value("${spring.kafka.producer.value-serializer:org.apache.kafka.common.serialization.StringSerializer}")
    private String valueSerializer;


    @Bean
    public ProducerFactory<String, String> producerFactory() {

        log.info("Конфигурация Kafka ProducerFactory для transfer-service. Адрес брокера: {}", bootstrapServers);
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);

        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, retryBackoffMs);

        log.debug("Свойства отправки Kafka в transfer-service: acks={}, retries={}, retryBackoffMs={}ms",
                acks, retries, retryBackoffMs);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        log.info("Регистрация бина KafkaTemplate для публикации транзакционных событий перевода...");
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory());
        kafkaTemplate.setObservationEnabled(true);

        log.info("Бин KafkaTemplate успешно создан. Опция setObservationEnabled установлена в true.");

        return kafkaTemplate;
    }

    @Bean
    public NewTopic bankNotificationsTopic() {

        log.info("Декларативное создание топика Kafka '{}'" +
                " (Partitions: 1, Replicas: 1) при старте контекста.", notificationTopic);
        return TopicBuilder.name(notificationTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}



