FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode clean package

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system spring \
    && useradd --system --gid spring spring

WORKDIR /app

COPY --from=builder --chown=spring:spring \
    /workspace/target/config-server-*.jar app.jar

COPY --chown=spring:spring config-repo/ config-repo/

USER spring:spring

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "app.jar"]
