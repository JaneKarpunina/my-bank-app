package ru.yandex.practicum.cash.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaProducerConfig {

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

        log.info("Конфигурация Kafka ProducerFactory для cash-service." +
                " Адрес подключения к брокеру: {}", bootstrapServers);
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);

        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, retryBackoffMs);

        log.debug("Параметры доставки сообщений Kafka в cash-service: acks={}, retries={}, retryBackoffMs={}ms",
                acks, retries, retryBackoffMs);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {

        log.info("Регистрация бина KafkaTemplate для публикации транзакционных событий кассы...");

        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory());
        kafkaTemplate.setObservationEnabled(true);

        log.info("Бин KafkaTemplate успешно создан. Опция setObservationEnabled активирована.");

        return kafkaTemplate;
    }
}


