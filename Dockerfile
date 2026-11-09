# Karton adrese – izgradnja i pokretanje u kontejneru
# 1) izgradnja JAR-a (Maven, Java 21)
FROM maven:3.9-eclipse-temurin-21 AS gradnja
WORKDIR /src
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src src
COPY model model
RUN mvn -B -ntp -q -DskipTests package

# 2) izvođenje (samo JRE)
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 karton
COPY --from=gradnja /src/target/karton-adrese-*.jar /app/karton-adrese.jar
COPY model /app/model
USER karton
EXPOSE 8080
ENV KARTON_MODEL_MAPA=/app/model
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/karton-adrese.jar"]
