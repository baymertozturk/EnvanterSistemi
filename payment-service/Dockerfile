# =========================================================================
# Stage 1: Build Stage (Maven + JDK 25)
# Multi-stage build mimarisi: Kaynak kodları derlemek için tam JDK ve Maven
# araçlarını içeren geçici bir builder container kullanılır.
# =========================================================================
FROM maven:3.9.11-eclipse-temurin-25-alpine AS builder

WORKDIR /build

# 1. Dependency Caching: Parent POM ve tüm modül POM dosyalarını kopyala
COPY pom.xml .
COPY order-service/pom.xml order-service/
COPY inventory-service/pom.xml inventory-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/

# 2. Kaynak kodları kopyala
COPY payment-service/src payment-service/src

# 3. Sadece payment-service modülünü derle ve paketle (-am: also-make bağımlılıkları)
RUN mvn clean package -DskipTests -pl payment-service -am

# =========================================================================
# Stage 2: Runtime Stage (Slim JRE 25 Alpine)
# Derleyici ve kaynak kodlar atılır, sadece minimal JRE ve jar çalıştırılır.
# =========================================================================
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Güvenlik standardı: root olmayan uygulama kullanıcısı oluştur
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Sadece üretilen çalıştırılabilir JAR dosyasını kopyala
COPY --from=builder /build/payment-service/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
