package com.hcl.hackathon.notificationservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Immutable event consumed from the {@code card.status} Kafka topic when a
 * credit-card application reaches a terminal or error state.
 */
@Value
@Jacksonized
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationStatusEvent {

    String applicationId;
    String email;
    String customerName;
    ApplicationStatus status;
    String remarks;

    @Builder.Default
    Instant eventTimestamp = Instant.now();

    /** Outcome states for a credit-card application workflow. */
    public enum ApplicationStatus {
        APPROVED,
        REJECTED,
        ERROR
    }
}
