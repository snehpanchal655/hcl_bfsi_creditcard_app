package com.creditcard.notification.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages Mapped Diagnostic Context (MDC) values for Kafka consumer threads.
 *
 * <h3>Why MDC matters in Kafka consumers</h3>
 * Unlike HTTP request threads (where a servlet filter sets/clears MDC),
 * Kafka consumer threads are long-lived and reused across multiple poll loops.
 * Failing to call {@link #clear()} after each record means the correlation-id
 * from record N leaks into record N+1, producing misleading logs.
 *
 * <h3>Usage pattern (try-finally is mandatory)</h3>
 * <pre>{@code
 *   mdcContextManager.populateFromHeaders(headers);
 *   try {
 *       // ... process record ...
 *   } finally {
 *       mdcContextManager.clear();   // ALWAYS runs, even on exception
 *   }
 * }</pre>
 */
@Slf4j
@Component
public class MdcContextManager {

    /** Header name expected in every Kafka message. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** MDC key used in log patterns (e.g., {@code %X{correlationId}}). */
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";

    /** MDC key carrying the Kafka topic name for each processed record. */
    public static final String MDC_TOPIC_KEY = "kafkaTopic";

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Extracts the {@code X-Correlation-Id} header from the Kafka record headers
     * and stores it in the current thread's MDC.
     *
     * <p>If the header is absent or blank, a new UUID is generated so that every
     * log statement is still traceable (at the cost of not correlating across
     * service boundaries for that one record).
     *
     * @param headers  Kafka record headers
     * @param topic    Kafka topic name, also injected into MDC for log filtering
     */
    public void populateFromHeaders(Headers headers, String topic) {
        String correlationId = extractHeader(headers, CORRELATION_ID_HEADER)
                .filter(v -> !v.isBlank())
                .orElseGet(() -> {
                    String generated = UUID.randomUUID().toString();
                    log.debug("No {} header found; generated correlationId={}",
                            CORRELATION_ID_HEADER, generated);
                    return generated;
                });

        MDC.put(MDC_CORRELATION_ID_KEY, correlationId);
        MDC.put(MDC_TOPIC_KEY, topic);
    }

    /**
     * Removes all MDC keys set by this manager from the current thread.
     *
     * <p><b>Must</b> be called in a {@code finally} block to prevent context leak
     * across Kafka poll iterations on the same consumer thread.
     */
    public void clear() {
        MDC.remove(MDC_CORRELATION_ID_KEY);
        MDC.remove(MDC_TOPIC_KEY);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Optional<String> extractHeader(Headers headers, String key) {
        if (headers == null) return Optional.empty();

        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) return Optional.empty();

        return Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }
}
