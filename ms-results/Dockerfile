FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src ./src
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S crystal && adduser -S crystal -G crystal
COPY --from=build /app/target/*.jar app.jar
USER crystal
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
