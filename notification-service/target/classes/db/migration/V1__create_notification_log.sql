-- Audit trail for credit-card application notifications (email and future channels).
CREATE TABLE notification_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_id VARCHAR(64) NOT NULL,
    application_status VARCHAR(32) NOT NULL,
    notification_channel VARCHAR(16) NOT NULL,
    delivery_status VARCHAR(16) NOT NULL,
    recipient_email_hash VARCHAR(64) NULL,
    correlation_id VARCHAR(64) NULL,
    kafka_topic VARCHAR(255) NULL,
    kafka_partition INT NULL,
    kafka_offset BIGINT NULL,
    error_message VARCHAR(1024) NULL,
    event_timestamp_utc TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    KEY idx_notification_app_created (application_id, created_at),
    KEY idx_notification_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
