FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy Maven wrapper and pom.xml first for better caching
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn
COPY pom.xml .

# Fix permissions for mvnw
RUN chmod +x ./mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src


# Expose port
EXPOSE 8080



# Build the application
RUN ./mvnw package -DskipTests && mv target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]

