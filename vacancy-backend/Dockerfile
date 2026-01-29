FROM eclipse-temurin:17-jdk-alpine as builder

WORKDIR /app

# Устанавливаем Maven
RUN apk add --no-cache maven

# Контракт общих DTO
COPY subscription-contract/pom.xml subscription-contract/pom.xml
COPY subscription-contract/src subscription-contract/src
RUN mvn -f subscription-contract/pom.xml -DskipTests install

# Копируем pom.xml для установки зависимостей
COPY vacancy-backend/pom.xml .

# Скачиваем зависимости
RUN mvn dependency:go-offline -B

# Копируем исходный код
COPY vacancy-backend/src ./src

# Собираем приложение
RUN mvn clean package -DskipTests

# Production stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Создаем пользователя для безопасности
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем JAR из builder stage
COPY --from=builder /app/target/vacancy-backend-*.jar app.jar

# Настройки JVM для production
ENV JAVA_OPTS="-Xmx512m -Xms256m -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/api/actuator/health || exit 1

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
