# Snaplink (URL Shortener)

Snaplink is a Spring Boot based URL shortener with analytics support.

## Tech Stack

- Java + Spring Boot
- PostgreSQL (primary data store)
- Redis (cache)
- Kafka (click event stream)
- Thymeleaf (server-side templates)

## Prerequisites

- Java 23 (for local Maven run)
- Maven Wrapper (already included: mvnw / mvnw.cmd)
- Docker (for containerized run)
- A running PostgreSQL instance
- Optional but recommended: Redis and Kafka

## Required Environment Variables

The app reads configuration from environment variables (see application.properties):

- DATABASE_URL
- DATABASE_USERNAME
- DATABASE_PASSWORD
- REDIS_HOST (default: localhost)
- REDIS_PORT (default: 6379)
- REDIS_TIMEOUT (default: 2000ms)

Example values:

DATABASE_URL=jdbc:postgresql://localhost:5432/snaplink
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_TIMEOUT=2000ms

## Run Locally (without Docker)

Windows PowerShell:

1) Set env vars:

$env:DATABASE_URL="jdbc:postgresql://localhost:5432/snaplink"
$env:DATABASE_USERNAME="postgres"
$env:DATABASE_PASSWORD="postgres"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_TIMEOUT="2000ms"

2) Start app:

./mvnw.cmd spring-boot:run

The application runs on:

http://localhost:8080

## Run With Docker

### 1) Build image

docker build -t snaplink:latest .

### 2) Run container

Use your actual database and Redis host values. If PostgreSQL/Redis are running on your host machine and your Docker supports it, host.docker.internal works on Windows and Mac.

docker run --name snaplink-app -p 8080:8080 ^
  -e DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/snaplink" ^
  -e DATABASE_USERNAME="postgres" ^
  -e DATABASE_PASSWORD="postgres" ^
  -e REDIS_HOST="host.docker.internal" ^
  -e REDIS_PORT="6379" ^
  -e REDIS_TIMEOUT="2000ms" ^
  snaplink:latest

For Linux, replace host.docker.internal with your host IP (or run all services in a Docker network).

### 3) Verify

Open:

- http://localhost:8080
- http://localhost:8080/actuator/health

## Notes

- The Dockerfile uses Eclipse Temurin JDK 23, aligned with pom.xml Java version 23.
- Kafka bootstrap server is currently configured in application.properties as a fixed IP.
  Make sure it is reachable from where the app is running.

## Useful Commands

Stop container:

docker stop snaplink-app

Remove container:

docker rm snaplink-app

View logs:

docker logs -f snaplink-app
