
# Install SQLite
FROM ubuntu:focal
RUN apt-get -y update
# RUN sudo apt-get -y upgrade
RUN apt-get install -y sqlite3 libsqlite3-dev


RUN mkdir -p /tinyurl-backend
WORKDIR /tinyurl-backend
RUN echo PWD=$PWD
RUN ls -al /tinyurl-backend/

FROM openjdk:8-jdk-alpine
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} /tinyurl-backend/app.jar
ENTRYPOINT java -jar /tinyurl-backend/app.jar

EXPOSE 7700
