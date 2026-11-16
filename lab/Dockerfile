FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp test package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /source/target/safe-ir-live-lab-0.2.0-SNAPSHOT.jar app.jar
RUN useradd --system --uid 10001 labuser
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
