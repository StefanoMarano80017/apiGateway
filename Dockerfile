# Usa un'immagine base di JDK per eseguire l'applicazione
FROM eclipse-temurin:17-jdk AS build

# Imposta la directory di lavoro all'interno del container
WORKDIR /app

# Copia il file JAR generato da Maven
COPY target/apiGateway-0.0.1-SNAPSHOT.jar app.jar

# Comando per eseguire l'applicazione
ENTRYPOINT ["java", "-jar", "app.jar"]
