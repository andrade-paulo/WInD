# Compila a aplicação usando Maven
FROM maven:3.8.4-openjdk-11 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Cria uma imagem final leve com apenas o JAR e o JRE
FROM openjdk:11-jre-slim
WORKDIR /app

# Copia o JAR compilado do estágio de build
COPY --from=build /app/target/*.jar app.jar

# Expõe a porta que a aplicação usará
# (A porta real está definida no docker-compose.yml)
ENTRYPOINT ["java","-jar","/app/app.jar"]