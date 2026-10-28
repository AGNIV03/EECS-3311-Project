# Library Agent web version.
# Build: docker build -t library-agent .   (normally via docker compose, see deploy/DEPLOY.md)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Download Gradle and dependencies first so they are cached between builds.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon -q installDist -x test

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 app \
    && mkdir -p /app/data && chown app /app/data
WORKDIR /app
COPY --from=build /src/build/install/library-agent/lib ./lib
USER app
ENV DB_PATH=/app/data/library.db \
    WEB_PORT=8080
VOLUME /app/data
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=75", "-cp", "lib/*", "libraryagent.ui.web.WebServer"]
