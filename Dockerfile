FROM eclipse-temurin:17 AS builder
WORKDIR /build
COPY . .
RUN chmod +x gradlew && ./gradlew build -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/build/libs/ProyectoFinalWeb-1.0-SNAPSHOT.jar app.jar
EXPOSE 7000 50051
CMD ["java", "-jar", "app.jar"]