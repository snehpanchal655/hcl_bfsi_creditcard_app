package com.creditcard.notification.config;

import com.creditcard.notification.model.ApplicationStatusEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration.
 *
 * <h3>Retry approach</h3>
 * We rely on {@code @RetryableTopic} (non-blocking, topic-based retry) declared
 * on the listener itself rather than a {@code DefaultErrorHandler} bean.
 * {@code DefaultErrorHandler} is kept commented out below to illustrate the
 * alternative approach — use it when blocking retries on the consumer thread
 * are acceptable (e.g., lower throughput services with simple linear backoff).
 *
 * <h3>Acknowledgement mode</h3>
 * {@code MANUAL_IMMEDIATE} gives us explicit control over offset commits,
 * which is important so a failed record isn't silently skipped.
 * {@code @RetryableTopic} handles ack internally, so no manual ack call is
 * needed in the listener method itself.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:notification-service-group}")
    private String groupId;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    // -------------------------------------------------------------------------
    // ConsumerFactory
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, ApplicationStatusEvent> consumerFactory(ObjectMapper objectMapper) {
        JsonDeserializer<ApplicationStatusEvent> deserializer =
                new JsonDeserializer<>(ApplicationStatusEvent.class, objectMapper);

        // Allow messages from any package (producer may not share our package)
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(false);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,           groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");
        // Disable auto-commit; Spring Kafka manages offsets based on ack mode
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Reasonable fetch limits to prevent memory pressure
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,   10);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer);
    }

    // -------------------------------------------------------------------------
    // Listener Container Factory
    // -------------------------------------------------------------------------

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ApplicationStatusEvent>
    kafkaListenerContainerFactory(ConsumerFactory<String, ApplicationStatusEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, ApplicationStatusEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // MANUAL_IMMEDIATE: offset is committed immediately after listener returns
        // (or after the retry infrastructure decides to move the record)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // Single-threaded consumer — increase for high-throughput topics
        factory.setConcurrency(1);

        /*
         * NOTE: DefaultErrorHandler is NOT configured here because @RetryableTopic
         * (non-blocking retry) is used on the listener.  The two mechanisms should
         * not be combined as they would double-retry messages.
         *
         * If you prefer DefaultErrorHandler instead of @RetryableTopic, replace
         * the @RetryableTopic annotation on the consumer and uncomment:
         *
         *   ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
         *   backOff.setInitialInterval(1_000L);
         *   backOff.setMultiplier(2.0);
         *   backOff.setMaxInterval(30_000L);
         *
         *   DeadLetterPublishingRecoverer recoverer =
         *       new DeadLetterPublishingRecoverer(kafkaTemplate,
         *           (rec, ex) -> new TopicPartition(rec.topic() + "-dlt", rec.partition()));
         *
         *   DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
         *   factory.setCommonErrorHandler(errorHandler);
         */

        return factory;
    }
}
