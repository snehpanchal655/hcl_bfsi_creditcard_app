package com.hcl.hackathon.notificationservice.kafka;

import com.hcl.hackathon.notificationservice.model.ApplicationStatusEvent;
import com.hcl.hackathon.notificationservice.service.NotificationLogService;
import com.hcl.hackathon.notificationservice.service.NotificationService;
import com.hcl.hackathon.notificationservice.util.DataMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes credit-card application status events from {@code card.status} and
 * triggers applicant notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardStatusEventConsumer {

    private static final String TOPIC = "card.status";
    private static final String GROUP_ID = "notification-service-group";
    private static final String CONTAINER_FACTORY = "kafkaListenerContainerFactory";

    private final NotificationService notificationService;
    private final MdcContextManager mdcContextManager;
    private final NotificationLogService notificationLogService;
    private final DataMaskingUtil dataMaskingUtil;

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1_000L, multiplier = 2.0, maxDelay = 30_000L),
            dltTopicSuffix = "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            include = {Exception.class},
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = TOPIC,
            groupId = GROUP_ID,
            containerFactory = CONTAINER_FACTORY
    )
    public void onCardStatusEvent(
            ConsumerRecord<String, ApplicationStatusEvent> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {

        mdcContextManager.populateFromHeaders(record.headers(), receivedTopic);

        try {
            ApplicationStatusEvent event = record.value();
            if (event == null) {
                log.error("Null ApplicationStatusEvent payload | topic={} | partition={} | offset={}",
                        receivedTopic, record.partition(), record.offset());
                notificationLogService.recordMalformed(record, "Null ApplicationStatusEvent payload");
                return;
            }

            log.info("Received card.status event | topic={} | partition={} | offset={} | status={}",
                    receivedTopic,
                    record.partition(),
                    record.offset(),
                    event.getStatus());

            notificationService.processNotification(event, KafkaMessageContext.from(record));

        } catch (Exception ex) {
            log.warn("Event processing failed; will retry or route to DLQ | error={}",
                    ex.getMessage(), ex);
            throw ex;

        } finally {
            mdcContextManager.clear();
        }
    }

    @KafkaListener(
            topics = TOPIC + "-dlt",
            groupId = GROUP_ID + "-dlt",
            containerFactory = CONTAINER_FACTORY
    )
    public void onDeadLetter(ConsumerRecord<String, ApplicationStatusEvent> record) {
        mdcContextManager.populateFromHeaders(record.headers(), record.topic());

        try {
            ApplicationStatusEvent event = record.value();
            String masked = event != null
                    ? dataMaskingUtil.maskApplicationId(event.getApplicationId())
                    : "UNKNOWN";

            log.error("Message sent to DLQ after retries | topic={} | partition={} | offset={} | applicationId={} | status={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    masked,
                    event != null ? event.getStatus() : "UNKNOWN");

            notificationLogService.recordDeadLetter(record, event);

        } finally {
            mdcContextManager.clear();
        }
    }
}
