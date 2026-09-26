FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
# Флаги памяти и GC приходят из переменной JAVA_OPTS (docker-compose.yml).
# JVM сам её не читает — это соглашение shell-скриптов, поэтому без обёртки
# ниже -Xmx/-Xms просто игнорировались, и heap считался по ergonomics.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]