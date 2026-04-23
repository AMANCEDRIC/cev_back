## =====================================================
## Stage 1 : Build Maven
## =====================================================
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Copier pom.xml et télécharger les dépendances en cache
COPY pom.xml .
RUN apt-get update && apt-get install -y maven && \
    mvn dependency:go-offline -q

# Copier les sources et compiler
COPY src ./src
RUN mvn package -DskipTests -q

## =====================================================
## Stage 2 : Image finale légère
## =====================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copier le JAR Quarkus (runner)
COPY --from=build /app/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/target/quarkus-app/*.jar /app/
COPY --from=build /app/target/quarkus-app/app/ /app/app/
COPY --from=build /app/target/quarkus-app/quarkus/ /app/quarkus/

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/quarkus-run.jar"]
