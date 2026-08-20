package com.example.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis yapılandırması.
 *
 * RedisTemplate<String, String> bean'i oluşturur. Bu template,
 * Kafka consumer'da idempotency kontrolü için kullanılır:
 *
 * Akış:
 * 1. Kafka event gelir (eventId içerir)
 * 2. Consumer, Redis'te "processed:{eventId}" key'i var mı kontrol eder
 * 3. Varsa → duplike event, atla (log: "duplicate event ignored")
 * 4. Yoksa → event'i işle, sonra Redis'e kaydet (TTL: 24 saat)
 *
 * Neden StringRedisSerializer?
 * - Key ve value'lar sadece string (UUID ve "true")
 * - JdkSerializationRedisSerializer'ın aksine, Redis CLI'dan okunabilir
 * - Daha az bellek kullanır (binary Java serialization overhead yok)
 *
 * TTL neden 24 saat?
 * - Kafka'nın varsayılan retention süresi 7 gün
 * - 24 saat, consumer group rebalance veya restart senaryolarında
 *   duplike event'leri yakalamak için yeterli bir pencere
 * - Sonsuza kadar tutmaya gerek yok (bellek tasarrufu)
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key ve value serializer olarak StringRedisSerializer kullan.
        // Bu sayede Redis CLI'dan direkt okunabilir:
        //   redis-cli> KEYS processed:*
        //   redis-cli> GET processed:550e8400-e29b-41d4-a716-446655440000
        //   → "true"
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
