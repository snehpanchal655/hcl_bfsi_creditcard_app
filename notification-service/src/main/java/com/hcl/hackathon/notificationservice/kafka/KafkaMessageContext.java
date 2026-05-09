package com.hcl.hackathon.notificationservice.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Minimal Kafka metadata stored with each notification for traceability
 * (replay, support, reconciliation with broker offsets).
 */
public record KafkaMessageContext(String topic, Integer partition, Long offset) {

    public static KafkaMessageContext from(ConsumerRecord<?, ?> record) {
        if (record == null) {
            return new KafkaMessageContext(null, null, null);
        }
        return new KafkaMessageContext(record.topic(), record.partition(), record.offset());
    }
}
