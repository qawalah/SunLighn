# SunWhere server: one image, runs on Fly.io or any machine with Docker.
#   docker build -t sunwhere-server server/
#   docker run -p 8080:8080 -v sunwhere-data:/data -e PUBLIC_URL=https://guides.example.com -e ADMIN_TOKEN=... sunwhere-server
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /src
COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle --no-daemon -q installDist

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home /data sunwhere && mkdir -p /data && chown sunwhere /data
COPY --from=build /src/build/install/sunwhere-server /opt/sunwhere
USER sunwhere
ENV DATA_DIR=/data PORT=8080 JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=70 -Xss512k"
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s CMD curl -fs http://localhost:8080/healthz || exit 1
ENTRYPOINT ["/opt/sunwhere/bin/sunwhere-server"]
