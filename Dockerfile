# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Strip CRLF in case the source was uploaded from a Windows checkout (sh would fail on "\r").
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
# Separate layer so dependency downloads are cached across builds unless build.gradle changes.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar boot.jar
# Unpacked jars load faster than nested ones. Vercel gives a container about 28s to open its port.
RUN java -Djarmode=tools -jar boot.jar extract --destination /app/run && rm boot.jar
WORKDIR /app/run

# Vercel's Container runtime routes to port 80 unless the project sets PORT; server.port reads PORT.
ENV PORT=80
EXPOSE 80
# Use most of the container's memory for the heap (the JVM default is only 25%). The other two trade peak speed for a faster start.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -XX:+UseSerialGC"
# Without prod or stage, the required settings have defaults and a missing Firebase key lets every
# request in as a fake dev user. So a container never starts on any other profile.
ENTRYPOINT ["sh", "-c", "case \"$SPRING_PROFILES_ACTIVE\" in prod|stage) exec java -jar boot.jar ;; *) echo 'SPRING_PROFILES_ACTIVE must be prod or stage' >&2; exit 1 ;; esac"]
