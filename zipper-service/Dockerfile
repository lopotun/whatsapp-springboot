FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw clean package -DskipTests

# Create runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install necessary packages
RUN apk add --no-cache curl

# Copy the built JAR
COPY --from=0 /app/target/*.jar app.jar

# Create directories for storage
RUN mkdir -p /app/storage /app/temp

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8081/zipper/api/v1/jobs/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
