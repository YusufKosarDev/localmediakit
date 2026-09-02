# --- Build stage ---
# A JDK image rather than a Maven one: the wrapper decides which Maven builds
# this project, and pulling in maven:3.9-* would put a second answer to that
# question inside the image, where it would quietly win over the pinned one.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Wrapper and pom first, so the dependency layer is only rebuilt when they
# change -- src/ churns on every commit and must not invalidate it.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -e -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -e -B clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Render injects PORT; Spring reads it via server.port=${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
