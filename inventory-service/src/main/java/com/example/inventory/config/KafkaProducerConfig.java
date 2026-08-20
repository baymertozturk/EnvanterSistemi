package com.example.inventory.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer yapılandırması — inventory-service.
 *
 * Bu servis iki topic'e event yayınlar:
 * - "stock-reserved" → stok başarıyla rezerve edildiğinde
 * - "stock-rejected" → stok yetersiz olduğunda
 *
 * TYPE_MAPPINGS sayesinde her event sınıfının kısa adı header'a yazılır.
 * order-service tarafında bu kısa adlar kendi yerel sınıflarına map edilir.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Type mapping: kısa ad → tam sınıf yolu
        props.put(JsonSerializer.TYPE_MAPPINGS,
                "StockReservedEvent:com.example.inventory.kafka.event.StockReservedEvent,"
              + "StockRejectedEvent:com.example.inventory.kafka.event.StockRejectedEvent");

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true);
        return template;
    }
}
