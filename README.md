Who Am I
========
Spring-boot web application to display host name and system resources. Specifically created to create docker image and use it to test Reverse Proxy capabilities of [Traefik](https://traefik.io/) with [Docker Swarm](https://docs.docker.com/engine/swarm/)

----------

Usage
-----
Clone the repository and run the below command to create springboot-whoami.jar

    gradle clean build

Create Docker image using the provided dockerfile

    FROM openjdk:8
    
    MAINTAINER Madan Narra <narra.madan@outlook.com>
    
	COPY ./build/libs/spring-boot-whoami.jar spring-boot-whoami.jar
	
	CMD java -jar spring-boot-whoami.jar

Command

    Docker build -t springboot-whoami .

Spin up two containers to test run the application
	
	Docker run -it -p 8080:8080 --name whoami0 springboot-whoami
	
	Docker run -it -p 8090:8080 --name whoami1 springboot-whoami
	
Accessing the application using https://localhost:8080 and https://localhost:8090 will display the container id next to 'Who Am I'

![Output](/resources/output.jpg?raw=true "Output")

![Containers](/resources/containers.jpg?raw=true "Containers")