package com.example.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic'lerini otomatik oluşturur.
 * Servis başlatıldığında bu topic'ler yoksa Kafka'da yaratılır.
 *
 * partition sayısı = 3: Paralel consumer desteği için.
 * replicas = 1: Tek node'lu geliştirme ortamı için yeterli.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name("order-created")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
