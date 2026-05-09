package com.hcl.hackathon.notificationservice.service;

import com.hcl.hackathon.notificationservice.kafka.KafkaMessageContext;
import com.hcl.hackathon.notificationservice.kafka.MdcContextManager;
import com.hcl.hackathon.notificationservice.model.ApplicationStatusEvent;
import com.hcl.hackathon.notificationservice.persistence.DeliveryStatus;
import com.hcl.hackathon.notificationservice.persistence.NotificationChannel;
import com.hcl.hackathon.notificationservice.persistence.NotificationLogRepository;
import com.hcl.hackathon.notificationservice.persistence.entity.NotificationLog;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Persists notification outcomes to {@code notification_log} for credit-card
 * application correspondence (compliance, support, reconciliation).
 */
@Service
@RequiredArgsConstructor
public class NotificationLogService {

    private static final int MAX_ERROR_LEN = 1024;

    private final NotificationLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            ApplicationStatusEvent event,
            KafkaMessageContext kafka,
            String correlationId) {

        NotificationLog row = NotificationLog.builder()
                .applicationId(event.getApplicationId())
                .applicationStatus(event.getStatus())
                .notificationChannel(NotificationChannel.EMAIL)
                .deliveryStatus(DeliveryStatus.SENT)
                .recipientEmailHash(sha256Email(event.getEmail()))
                .correlationId(correlationId)
                .kafkaTopic(kafka.topic())
                .kafkaPartition(kafka.partition())
                .kafkaOffset(kafka.offset())
                .errorMessage(null)
                .eventTimestampUtc(event.getEventTimestamp())
                .build();
        repository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            ApplicationStatusEvent event,
            KafkaMessageContext kafka,
            String correlationId,
            String message) {

        NotificationLog row = NotificationLog.builder()
                .applicationId(event.getApplicationId())
                .applicationStatus(event.getStatus())
                .notificationChannel(NotificationChannel.EMAIL)
                .deliveryStatus(DeliveryStatus.FAILED)
                .recipientEmailHash(sha256Email(event.getEmail()))
                .correlationId(correlationId)
                .kafkaTopic(kafka.topic())
                .kafkaPartition(kafka.partition())
                .kafkaOffset(kafka.offset())
                .errorMessage(truncate(message))
                .eventTimestampUtc(event.getEventTimestamp())
                .build();
        repository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMalformed(ConsumerRecord<String, ?> record, String reason) {
        KafkaMessageContext ctx = KafkaMessageContext.from(record);
        NotificationLog row = NotificationLog.builder()
                .applicationId("UNKNOWN")
                .applicationStatus(ApplicationStatusEvent.ApplicationStatus.ERROR)
                .notificationChannel(NotificationChannel.EMAIL)
                .deliveryStatus(DeliveryStatus.FAILED)
                .recipientEmailHash(null)
                .correlationId(MDC.get(MdcContextManager.MDC_CORRELATION_ID_KEY))
                .kafkaTopic(ctx.topic())
                .kafkaPartition(ctx.partition())
                .kafkaOffset(ctx.offset())
                .errorMessage(truncate(reason))
                .eventTimestampUtc(null)
                .build();
        repository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeadLetter(ConsumerRecord<String, ApplicationStatusEvent> record, ApplicationStatusEvent event) {
        KafkaMessageContext ctx = KafkaMessageContext.from(record);
        String correlationId = MDC.get(MdcContextManager.MDC_CORRELATION_ID_KEY);
        if (event == null) {
            NotificationLog row = NotificationLog.builder()
                    .applicationId("UNKNOWN")
                    .applicationStatus(ApplicationStatusEvent.ApplicationStatus.ERROR)
                    .notificationChannel(NotificationChannel.EMAIL)
                    .deliveryStatus(DeliveryStatus.FAILED)
                    .recipientEmailHash(null)
                    .correlationId(correlationId)
                    .kafkaTopic(ctx.topic())
                    .kafkaPartition(ctx.partition())
                    .kafkaOffset(ctx.offset())
                    .errorMessage(truncate("[DLQ] Null or unreadable payload"))
                    .eventTimestampUtc(null)
                    .build();
            repository.save(row);
            return;
        }

        NotificationLog row = NotificationLog.builder()
                .applicationId(event.getApplicationId())
                .applicationStatus(event.getStatus())
                .notificationChannel(NotificationChannel.EMAIL)
                .deliveryStatus(DeliveryStatus.FAILED)
                .recipientEmailHash(sha256Email(event.getEmail()))
                .correlationId(correlationId)
                .kafkaTopic(ctx.topic())
                .kafkaPartition(ctx.partition())
                .kafkaOffset(ctx.offset())
                .errorMessage(truncate("[DLQ] Exhausted retries for application notification email"))
                .eventTimestampUtc(event.getEventTimestamp())
                .build();
        repository.save(row);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LEN ? message : message.substring(0, MAX_ERROR_LEN);
    }

    private static String sha256Email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
