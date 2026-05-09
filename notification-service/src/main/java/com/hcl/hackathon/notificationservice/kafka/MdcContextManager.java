package com.hcl.hackathon.notificationservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps Kafka headers into SLF4J MDC for structured logging on consumer threads.
 */
@Slf4j
@Component
public class MdcContextManager {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";
    public static final String MDC_TOPIC_KEY = "kafkaTopic";

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

    public void clear() {
        MDC.remove(MDC_CORRELATION_ID_KEY);
        MDC.remove(MDC_TOPIC_KEY);
    }

    private Optional<String> extractHeader(Headers headers, String key) {
        if (headers == null) {
            return Optional.empty();
        }
        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) {
            return Optional.empty();
        }
        return Optional.of(new String(header.value(), StandardCharsets.UTF_8));
    }
}
