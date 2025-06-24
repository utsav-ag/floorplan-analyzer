# Base image with Java
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Maven files
COPY pom.xml .
COPY src ./src

# Install Maven and build the application
RUN apt-get update && \
    apt-get install -y maven && \
    mvn clean package && \
    rm -rf /var/lib/apt/lists/*

# Expose port 8080
EXPOSE 8080

# Set entry point
ENTRYPOINT ["java", "-jar", "target/floorplan-analyzer-1.0-SNAPSHOT.jar"]