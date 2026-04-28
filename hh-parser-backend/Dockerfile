FROM eclipse-temurin:17-jdk-alpine as builder
WORKDIR /app
RUN apk add --no-cache maven
COPY hh-parser-backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY hh-parser-backend/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl && addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /app/target/hh-parser-backend-*.jar app.jar
ENV JAVA_OPTS="-Xmx512m -Xms256m -Djava.security.egd=file:/dev/./urandom"
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8084/api/actuator/health || exit 1
EXPOSE 8084
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
