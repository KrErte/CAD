FROM gradle:8.10-jdk21 AS build
WORKDIR /src
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar

# SECURITY-FIX: non-root kasutaja — vähendab konteineri kompromiteerimise mõju
RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --ingroup appgroup appuser
USER appuser

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
