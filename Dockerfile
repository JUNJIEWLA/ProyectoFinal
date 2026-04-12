FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/ProyectoFinalWeb-1.0-SNAPSHOT.jar app.jar
EXPOSE 7000 50051
CMD ["java", "-jar", "app.jar"]

