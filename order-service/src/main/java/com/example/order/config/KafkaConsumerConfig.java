package com.example.order.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer yapılandırması.
 *
 * {@link EnableKafka} anotasyonu, {@link org.springframework.kafka.annotation.KafkaListener}
 * anotasyonlu metotların tespit edilip aktive edilmesini sağlar.
 *
 * {@link JsonDeserializer} kullanılarak gelen JSON mesajlar otomatik olarak
 * Java nesnelerine dönüştürülür. TYPE_MAPPINGS ile inventory-service'in
 * farklı paketindeki sınıflar, bu servisin yerel sınıflarına map edilir.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Tüm paketlerden gelen sınıflara güven (geliştirme ortamı için)
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        // inventory-service ve payment-service'ten gelen event'leri bu servisin sınıflarına map et
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "StockReservedEvent:com.example.order.kafka.event.StockReservedEvent,"
              + "StockRejectedEvent:com.example.order.kafka.event.StockRejectedEvent,"
              + "PaymentCompletedEvent:com.example.order.kafka.event.PaymentCompletedEvent,"
              + "PaymentFailedEvent:com.example.order.kafka.event.PaymentFailedEvent");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Distributed Tracing: Kafka consumer observation aktif et
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
