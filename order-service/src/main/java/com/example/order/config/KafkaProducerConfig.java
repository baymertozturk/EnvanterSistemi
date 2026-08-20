package com.example.order.config;

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
 * Kafka Producer yapılandırması.
 *
 * Neden Spring Boot auto-config yerine manuel yapılandırma?
 * 1. Value serializer olarak {@link JsonSerializer} kullanıyoruz (default StringSerializer yerine).
 *    Event nesneleri otomatik olarak JSON'a dönüştürülür.
 *
 * 2. {@link JsonSerializer#TYPE_MAPPINGS} ile event sınıflarının kısa adlarını tanımlıyoruz.
 *    Producer, Kafka mesaj header'ına kısa adı yazar (ör: "OrderCreatedEvent").
 *    Consumer tarafında bu kısa ad, o servisin kendi paketindeki sınıfa map edilir.
 *    Böylece her mikroservis kendi paket yapısını bağımsız olarak yönetebilir.
 *
 * Örnek akış:
 *   order-service (producer):
 *     "OrderCreatedEvent" → com.example.order.kafka.event.OrderCreatedEvent
 *   inventory-service (consumer):
 *     "OrderCreatedEvent" → com.example.inventory.kafka.event.OrderCreatedEvent
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
                "OrderCreatedEvent:com.example.order.kafka.event.OrderCreatedEvent");

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        // Distributed Tracing: Kafka producer observation aktif et (traceId header enjeksiyonu)
        template.setObservationEnabled(true);
        return template;
    }
}
