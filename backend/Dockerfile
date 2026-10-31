FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd -r -u 10001 jondeu && mkdir -p /data/uploads && chown -R jondeu /data
USER jondeu
WORKDIR /app
COPY --from=build /src/target/jondeu-backend-*.jar app.jar
ENV STORAGE_DIR=/data/uploads JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
