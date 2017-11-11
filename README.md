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

# Testing with Swarm mode cluster
To test springboot-whoami on Swarm mode cluster, provision 3 server instances with docker installed. Of the 3 server instances, one will act as Manager and the rest as Workers.

**Provisioning Servers on AWS**
1. Launch 'Ubuntu Server 16.04 LTS (HVM), SSD Volume Type - ami-67a6e604' with 't2.micro' for this test run.
2. Choose 3 instances and proceed to 'Security Group' configuration.
3. Below ports must be added for the docker engines to communicate in swarm mode
* TCP port 2377 for cluster management communications
* TCP and UDP port 7946 for communication among nodes
* UDP port 4789 for overlay network traffic

**Install Docker**
Follow the [instructions](https://docs.docker.com/engine/installation/linux/docker-ce/ubuntu/#install-docker-ce-1) provided to add docker repository . Install and update docker from the repository.

Update the apt package index.
```$ sudo apt-get update```

Install the latest version of Docker CE, or go to the next step to install a specific version. Any existing installation of Docker is replaced.
```$ sudo apt-get install docker-ce```

To run docker without sudo, run the below command and then completely log out of your account and log back in.
``` $ sudo usermod -a -G docker $USER ```

Run below command to verify if docker is up and running
``` $ docker info ```

**Test run springboot-whoami**
Before proceeding to testing whoami on swarm mode, test run the image by spinning a container on one of the server

```
$ docker pull narramadan/springboot-whoami

$ docker run -it -p 8080:8080 --name whoamitest narramadan/springboot-whoami
```

Accessing the application using ec2 public DNS http://ec2-xx-xx-xxx-xxx.ap-southeast-1.compute.amazonaws.com:8080/ should display the container id next to 'Who Am I'
```Who Am I - 3af3b374ded7```

Stop the container and remove the container reference before proceeding to Swarn mode testing

```
$ docker stop 3a

$ docker rm 3af3
```