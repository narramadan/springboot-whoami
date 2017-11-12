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
Follow the [instructions](https://docs.docker.com/engine/installation/linux/docker-ce/ubuntu/#install-using-the-repository) provided to add docker repository . Install and update docker from the repository.

Update the apt package index.
```
$ sudo apt-get update
```

Install the latest version of Docker CE, or go to the next step to install a specific version. Any existing installation of Docker is replaced.
```
$ sudo apt-get install docker-ce
```

To run docker without sudo, run the below command and then completely log out of your account and log back in.
```
$ sudo usermod -a -G docker $USER 
```

Run below command to verify if docker is up and running
```
$ docker info
```

**Test run springboot-whoami**
Before proceeding to testing whoami on swarm mode, test run the image by spinning a container on one of the server

```
$ docker pull narramadan/springboot-whoami

$ docker run -it -p 8080:8080 --name whoamitest narramadan/springboot-whoami
```

Accessing the application using ec2 public DNS http://ec2-xx-xx-xxx-xxx.ap-southeast-1.compute.amazonaws.com:8080/ should display the container id next to 'Who Am I'
```
Who Am I - 3af3b374ded7
```

Stop the container and remove the container reference before proceeding to Swarn mode testing

```
$ docker stop 3a

$ docker rm 3af3
```

**Creating Swarm Cluster**
SSH to machine which should run as Manager node and run the below command ``` ifconfig ``` to get the IP Address of the machine.

Run the below command to create a new swarm:
```
$ docker swarm init --advertise-addr <MANAGER-IP> 
```
This would return swarm join command which should be executed on the worker node machines
```
$ docker swarm join --token SWMTKN-1-0ayszwbf4fthuztfs127i9s5suc4lmkzjrz0p7vfrn5qgrzj66-bth68isnr9olw88orzpy611pj 172.31.0.30:2377
```

Running ```docker node ls``` will list down all the nodes and one being marked as 'Leader ' under Manager Status.

**Deploying WhoAmI service to Swarm**

Run the below command on the Manager node to deploy 3 whoamI image to the swarm. Manager node will distribute them evenly to all the three nodes
```
$ docker service create --replicas 3 --name whoami --publish 8080:8080 narramadan/springboot-whoami
```
Run ``` docker service ls``` to see the list of running services
```
ID                  NAME                MODE                REPLICAS            IMAGE                                 PORTS
vxleib8t4xqr        whoami              replicated          3/3                 narramadan/springboot-whoami:latest   *:8080->8080/tcp
```

Run ``` docker service logs <ID> ``` on the Manager node to see if the service has started successfully without any errors.

Running ``` docker ps -a ``` on Manager, Worker 1 & Worker 2 nodes will show the list of running containers

**Testing WhoAmI on created swarm service**

```ingress``` network is used whenever a swarm is initialized. This will do automatic load balancing among all the service nodes. When a request is sent to any of the node server on 8080, one of the node in the cluster will process the request and its container id is displayed in browser. 

Upon refreshing the browser, we don't see the container id changing. This is due to rounting mesh algorithm on ingress network. The swarm load balancer routes your request to any of the active container.

Wait for 5 minutes and refresh the browser. You can see swarm has routed to other container.

External load balancer such as ```nginx``` or ```traefik``` can be configured to achive roundroubin routing.

More information on ```ingress``` and its routing mesh can be found [here](https://docs.docker.com/engine/swarm/ingress/)

**Scale the Service Up or Down**

Additional containers can be spinned up across the nodes by running the below command. This will spin up additional 3 replicas across the nodes as we already have 3 running.
```
$ docker service scale whoami=6

whoami scaled to 6
```

```
ubuntu@ip-172-31-8-139:~$ docker ps -a
CONTAINER ID        IMAGE                                 COMMAND                  CREATED             STATUS              PORTS               NAMES
53b12845a328        narramadan/springboot-whoami:latest   "/bin/sh -c 'java ..."   34 minutes ago      Up 34 minutes                           whoami.1.vcgje0yylqne8ujgfnswvdxca
```

To ***scale down*** the service, run ``` docker service scale whoami=3``` to remove 3 containers evenly across the nodes.

**Publish image updates on swarm nodes**
Any code changes done to whoami should be applied to all the swarm nodes. This can be achievable by running the below command on Manager node.

```
$ docker service update --image narramadan/springboot-whoami whoami
```

Do ensure to update the image and push it to docker hub before running the above command

```
PS C:\Work> gradle clean build

PS C:\Work> Docker build -t springboot-whoami .

PS C:\Work> docker tag springboot-whoami narramadan/springboot-whoami

PS C:\Work> docker push narramadan/springboot-whoami
```

**Delete the Service**

Run the below command to remove the service from swarm. 

```$ docker service rm whoami```

Running this will take few seconds to clean up the containers across the nodes.

# Load Balancing Swarm nodes with nginx reverse proxy

# Load Balancing Swarm nodes with traefik reverse proxy