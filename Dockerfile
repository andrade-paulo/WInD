FROM maven:3.9.6-eclipse-temurin-17-focal AS build
WORKDIR /app

# Instalo logo o shared para que os outros módulos possam usá-lo.
COPY shared/pom.xml shared/pom.xml
COPY shared/src shared/src/
RUN mvn -f shared/pom.xml clean install -DskipTests

COPY service_discovery/pom.xml service_discovery/pom.xml
COPY service_discovery/src service_discovery/src/

COPY api_gateway/pom.xml api_gateway/pom.xml
COPY api_gateway/src api_gateway/src/

COPY application_server/pom.xml application_server/pom.xml
COPY application_server/src application_server/src/

COPY weather_station/pom.xml weather_station/pom.xml
COPY weather_station/src weather_station/src/

# Executa o 'package' para os módulos finais. Não precisamos mais do 'install'.
RUN mvn -f service_discovery/pom.xml clean package -DskipTests
RUN mvn -f api_gateway/pom.xml clean package -DskipTests
RUN mvn -f application_server/pom.xml clean package -DskipTests
RUN mvn -f weather_station/pom.xml clean package -DskipTests

# Instala o curl para o healthcheck do Docker
USER root
RUN apt-get update && apt-get install -y curl
USER app


FROM eclipse-temurin:17-jre-jammy AS service-discovery
WORKDIR /app
COPY --from=build /app/service_discovery/target/service_discovery*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM eclipse-temurin:17-jre-jammy AS api-gateway
WORKDIR /app
RUN apt-get update && apt-get install -y iproute2 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/api_gateway/target/api_gateway*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM eclipse-temurin:17-jre-jammy AS application-server
WORKDIR /app
COPY --from=build /app/application_server/target/application_server*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM eclipse-temurin:17-jre-jammy AS weather-station
WORKDIR /app
RUN apt-get update && apt-get install -y iproute2 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/weather_station/target/weather_station*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
RUN apt-get update && apt-get install -y iproute2 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/weather_station/target/weather_station*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]