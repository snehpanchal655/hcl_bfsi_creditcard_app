package com.creditcard.notification.kafka;

import com.creditcard.notification.model.ApplicationStatusEvent;
import com.creditcard.notification.service.NotificationService;
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
 * Kafka consumer for the {@code card.status} topic.
 *
 * <h3>Retry Strategy — {@code @RetryableTopic}</h3>
 * Spring Kafka's {@link RetryableTopic} is preferred over a custom
 * {@code DefaultErrorHandler} bean for these reasons:
 * <ul>
 *   <li>It uses <em>non-blocking</em> retry topics (no thread sleep, no consumer
 *       lag accumulation on the main partition).</li>
 *   <li>Exponential backoff is declarative and test-friendly.</li>
 *   <li>DLQ routing is automatic — zero boilerplate.</li>
 * </ul>
 *
 * <h3>Generated topic topology for {@code card.status}</h3>
 * <pre>
 *   card.status                       ← main topic (attempt 1)
 *   card.status-retry-0               ← retry attempt 2 (delay ≈ 1 s)
 *   card.status-retry-1               ← retry attempt 3 (delay ≈ 2 s)
 *   card.status-retry-2               ← retry attempt 4 (delay ≈ 4 s)
 *   card.status-dlt                   ← Dead Letter Topic after 3 retries
 * </pre>
 *
 * <h3>MDC lifecycle</h3>
 * {@code try-finally} guarantees the MDC is cleared even when the listener
 * throws, preventing correlation-id leakage to subsequent records on the same
 * consumer thread.
 *
 * <h3>SRP</h3>
 * This class owns only the Kafka integration contract. Business and email logic
 * are fully delegated to {@link NotificationService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardStatusEventConsumer {

    private static final String TOPIC              = "card.status";
    private static final String GROUP_ID           = "notification-service-group";
    private static final String CONTAINER_FACTORY  = "kafkaListenerContainerFactory";

    private final NotificationService notificationService;
    private final MdcContextManager   mdcContextManager;

    // -------------------------------------------------------------------------
    // Listener
    // -------------------------------------------------------------------------

    /**
     * Consumes {@link ApplicationStatusEvent} messages from {@code card.status}.
     *
     * <p>Retry configuration:
     * <ul>
     *   <li>3 retry attempts (4 total including the original)</li>
     *   <li>Exponential backoff: 1 s → 2 s → 4 s (multiplier = 2)</li>
     *   <li>After exhaustion → {@code card.status-dlt} (Dead Letter Topic)</li>
     * </ul>
     *
     * @param record      the raw Kafka consumer record (gives access to headers)
     * @param event       deserialised event payload
     * @param receivedTopic the actual topic this invocation is running on
     *                    (could be a retry topic or the main topic)
     */
    @RetryableTopic(
            attempts          = "4",                  // 1 original + 3 retries
            backoff           = @Backoff(
                    delay      = 1_000L,              // initial delay 1 s
                    multiplier = 2.0,                 // exponential: 1s → 2s → 4s
                    maxDelay   = 30_000L              // cap at 30 s for safety
            ),
            dltTopicSuffix             = "-dlt",
            topicSuffixingStrategy     = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            include                    = {Exception.class},  // retry on any exception
            autoCreateTopics           = "false"             // topics managed by infra
    )
    @KafkaListener(
            topics           = TOPIC,
            groupId          = GROUP_ID,
            containerFactory = CONTAINER_FACTORY
    )
    public void onCardStatusEvent(
            ConsumerRecord<String, ApplicationStatusEvent> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String receivedTopic) {

        // ── 1. Populate MDC from Kafka headers ────────────────────────────────
        mdcContextManager.populateFromHeaders(record.headers(), receivedTopic);

        try {
            ApplicationStatusEvent event = record.value();

            log.info("Received card.status event | topic={} | partition={} | offset={} | status={}",
                    receivedTopic,
                    record.partition(),
                    record.offset(),
                    event.getStatus());

            // ── 2. Delegate to the notification service ───────────────────────
            notificationService.processNotification(event);

        } catch (Exception ex) {
            // Log here before rethrowing so the correlationId is still in MDC
            log.warn("Event processing failed; will retry or route to DLQ | error={}",
                    ex.getMessage(), ex);
            throw ex;   // re-throw so @RetryableTopic machinery takes over

        } finally {
            // ── 3. ALWAYS clear MDC — prevents context leak across poll cycles ─
            mdcContextManager.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Dead Letter Topic handler
    // -------------------------------------------------------------------------

    /**
     * Handles messages that have exhausted all retry attempts.
     *
     * <p>Responsibilities here are intentionally minimal: log the failure with
     * full context so an on-call engineer can triage. A production system would
     * also:
     * <ul>
     *   <li>Publish a metric/alert (e.g., increment a Micrometer counter)</li>
     *   <li>Persist the failed event to a "poison pill" database table</li>
     *   <li>Trigger a PagerDuty / Slack alert</li>
     * </ul>
     *
     * @param record the dead-lettered record
     */
    @KafkaListener(
            topics           = TOPIC + "-dlt",
            groupId          = GROUP_ID + "-dlt",
            containerFactory = CONTAINER_FACTORY
    )
    public void onDeadLetter(ConsumerRecord<String, ApplicationStatusEvent> record) {
        mdcContextManager.populateFromHeaders(record.headers(), record.topic());

        try {
            ApplicationStatusEvent event = record.value();

            log.error("💀 Message sent to DLQ — exhausted all retries | "
                            + "topic={} | partition={} | offset={} | applicationId(masked)={} | status={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    event != null ? "masked-see-log-context" : "null-payload",
                    event != null ? event.getStatus() : "UNKNOWN");

            // TODO (production): persist to dead_letter_events table & raise alert

        } finally {
            mdcContextManager.clear();
        }
    }
}
