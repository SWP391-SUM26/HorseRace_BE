# syntax=docker/dockerfile:1
# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# maven.test.skip=true skips COMPILING tests too (the test sources don't compile);
# -DskipTests alone would still try to compile them and fail the build.
# A BuildKit cache mount keeps ~/.m2 across builds so deps aren't re-downloaded every time.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true clean package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
