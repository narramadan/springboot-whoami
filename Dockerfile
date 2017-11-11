FROM openjdk:8

MAINTAINER Madan Narra <narra.madan@outlook.com>

COPY ./build/libs/spring-boot-whoami.jar spring-boot-whoami.jar
CMD java -jar spring-boot-whoami.jar