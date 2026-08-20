package com.example.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic'lerini otomatik oluşturur.
 * inventory-service iki topic'e event yayınlar.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic stockReservedTopic() {
        return TopicBuilder.name("stock-reserved")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockRejectedTopic() {
        return TopicBuilder.name("stock-rejected")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
