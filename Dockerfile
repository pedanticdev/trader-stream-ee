# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /opt/payara
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*
RUN wget -O payara-micro.jar 'https://nexus.payara.fish/repository/payara-community/fish/payara/extras/payara-micro/7.2025.2/payara-micro-7.2025.2.jar'

COPY --from=build /app/target/*.war ROOT.war

CMD ["java", "-jar", "payara-micro.jar", "--deploy", "ROOT.war"]