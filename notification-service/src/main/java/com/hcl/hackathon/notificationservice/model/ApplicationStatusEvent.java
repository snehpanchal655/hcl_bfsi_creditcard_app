package com.creditcard.notification.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Immutable event POJO consumed from the {@code card.status} Kafka topic.
 *
 * <p>Uses {@link Value} + {@link Builder} for immutability and a fluent build API.
 * {@link JsonCreator} ensures Jackson can deserialize even when no-arg constructor
 * is absent (record-style deserialization).
 */
@Value
@Builder
public class ApplicationStatusEvent {

    /** Unique credit-card application identifier (e.g., APP-2024-00042). */
    String applicationId;

    /** Applicant email address — treat as PII, never log raw. */
    String email;

    /** Human-readable customer name used in email salutation. */
    String customerName;

    /** Decision outcome for the application. */
    ApplicationStatus status;

    /**
     * Optional human-readable message from underwriting or the error handler.
     * May be {@code null} for APPROVED events.
     */
    String remarks;

    /** Timestamp when the event was produced (ISO-8601 / epoch-ms). */
    @Builder.Default
    Instant eventTimestamp = Instant.now();

    @JsonCreator
    public ApplicationStatusEvent(
            @JsonProperty("applicationId")  String applicationId,
            @JsonProperty("email")          String email,
            @JsonProperty("customerName")   String customerName,
            @JsonProperty("status")         ApplicationStatus status,
            @JsonProperty("remarks")        String remarks,
            @JsonProperty("eventTimestamp") Instant eventTimestamp) {

        this.applicationId  = applicationId;
        this.email          = email;
        this.customerName   = customerName;
        this.status         = status;
        this.remarks        = remarks;
        this.eventTimestamp = eventTimestamp != null ? eventTimestamp : Instant.now();
    }

    /** Outcome states for a credit-card application. */
    public enum ApplicationStatus {
        APPROVED,
        REJECTED,
        ERROR
    }
}
