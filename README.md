Who Am I
========
Spring-boot web application to display containers host name and system resources. Specifically created to test [Docker Swarm](https://docs.docker.com/engine/swarm/) with [Traefik](https://traefik.io/) Reverse Proxy capabilities.

----------

Usage
-----
Clone the repository and run the below command to create springboot-whoami.jar
```
PS C:\Work> gradle clean build
```

Create Docker image using the provided dockerfile
```
FROM openjdk:8
    
MAINTAINER Madan Narra <narra.madan@outlook.com>
    
COPY ./build/libs/spring-boot-whoami.jar spring-boot-whoami.jar
	
CMD java -jar spring-boot-whoami.jar

```
Command to create docker image
```
PS C:\Work> Docker build -t springboot-whoami .
```

Push the image to docker hub
```
PS C:\Work> docker tag springboot-whoami narramadan/springboot-whoami

PS C:\Work> docker push narramadan/springboot-whoami
```

Spin up two containers to test run the application on your development environment
```
PS C:\Work> Docker run -it -p 8080:8080 --name whoami0 springboot-whoami
	
PS C:\Work> Docker run -it -p 8090:8080 --name whoami1 springboot-whoami
```

Accessing the application using https://localhost:8080 and https://localhost:8090 will display the container id next to 'Who Am I'

![Output](/resources/output.jpg?raw=true "Output")

![Containers](/resources/containers.jpg?raw=true "Containers")

# Testing `Who Am I` with `Docker Swarm`
To test springboot-whoami on Docker with Swarm Mode, provision 3 server instances with docker installed. Of the 3 server instances, one will act as Manager and the rest as Workers.

### Provisioning Servers on AWS
1. Launch `Ubuntu Server 16.04 LTS (HVM), SSD Volume Type - ami-67a6e604` with `t2.micro` for this test run.
2. Choose 3 instances and proceed to `Security Group` configuration.
3. Below ports must be added for the docker engines to communicate in swarm mode
* `TCP` port `2377` for cluster management communications
* `TCP` and `UDP` port `7946` for communication among nodes
* `UDP` port `4789` for overlay network traffic

### Install Docker
Follow the [instructions](https://docs.docker.com/engine/installation/linux/docker-ce/ubuntu/#install-using-the-repository) provided to add docker repository to download and install docker.

Update the apt package index.
```
$ sudo apt-get update
```

Install the latest version of Docker CE
```
$ sudo apt-get install docker-ce
```

Run the below command on all the 3 instances to run docker without sudo. Post execution, completely log out of your account and log back in.
```
$ sudo usermod -a -G docker $USER 
```

Run below command to verify if docker is up and running
```
$ docker info
```

### Test run springboot-whoami
Before proceeding to test whoami on swarm mode, verify the image by spinning a container on one of the server.

```
$ docker pull narramadan/springboot-whoami

$ docker run -it -p 8080:8080 --name whoamitest narramadan/springboot-whoami
```

Accessing the application using ec2 public DNS http://ec2-xx-xx-xxx-xxx.ap-southeast-1.compute.amazonaws.com:8080/ should display the container id next to 'Who Am I'
```
Who Am I - 3af3b374ded7
```

Stop the container and remove its reference before proceeding to configure Docker Swarn Mode

```
$ docker stop 3a

$ docker rm 3af3
```

### Creating Swarm Cluster
Identify the IP address of the instance which should run as Manager node. use command `ifconfig` to get the IP Address of the instance.

Run the below command to initialize swarm cluster:
```
$ docker swarm init --advertise-addr <MANAGER-IP> 
```

This would return swarm join command which should be executed on the 2 worker node machines
```
$ docker swarm join --token SWMTKN-1-0ayszwbf4fthuztfs127i9s5suc4lmkzjrz0p7vfrn5qgrzj66-bth68isnr9olw88orzpy611pj 172.31.0.30:2377
```

Running `docker node ls` will list down all the nodes and of the 3 nodes one will be marked as `Leader` under Manager Status.

### Deploying WhoAmI service to Swarm

Run the below command on the Manager node to deploy 3 whoamI image in the swarm. Manager node will distribute them evenly to all the three nodes
```
$ docker service create --replicas 3 --name whoami --publish 8080:8080 narramadan/springboot-whoami
```

Run ``` docker service ls``` to see the list of running services
```
ID                  NAME                MODE                REPLICAS            IMAGE                                 PORTS
vxleib8t4xqr        whoami              replicated          3/3                 narramadan/springboot-whoami:latest   *:8080->8080/tcp
```

Run `docker service logs <ID>` on the Manager node to see if the service has started successfully without any errors.

Running `docker ps -a` on Manager, Worker 1 & Worker 2 nodes will show the list of running containers

### Testing WhoAmI on created swarm service

[`Ingress`](https://en.wikipedia.org/wiki/Ingress_filtering) network is used whenever a swarm is initialized. This will do automatic load balancing among all the service nodes. When a request is sent to any of the node server on 8080, one of the node in the cluster will process the request and its container id is displayed in browser. 

Upon refreshing the browser, we don't see the container id changing. This is due to rounting mesh algorithm on ingress network. The swarm load balancer routes your request to any of the active container.

Wait for 2-3 minutes and refresh the browser. You can see swarm has routed to other container.

External load balancer such as `nginx` or `traefik` can be configured to achive roundroubin routing.

More information on `ingress` and its routing mesh can be found [`here`](https://docs.docker.com/engine/swarm/ingress/)

### Scale the Service Up or Down

Additional containers can be spinned up across the nodes by running the below command. This will spin up additional 3 replicas across the nodes as we already have 3 running.
```
$ docker service scale whoami=6

whoami scaled to 6
```

```
ID                  NAME                MODE                REPLICAS            IMAGE                                 PORTS
vxleib8t4xqr        whoami              replicated          6/6                 narramadan/springboot-whoami:latest   *:8080->8080/tcp
```

To ***scale down*** the service, run `docker service scale whoami=3` to remove 3 containers evenly across the nodes.

### Rolling image updates on swarm nodes
Any code changes done to whoami should be rolled to all the swarm nodes. This can be achievable by running the below command on Manager node.

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

### Delete the Service

Run the below command to remove the service from swarm. 

```
$ docker service rm whoami
```

Running this will take few seconds to clean up the containers across the nodes.

# Load Balancing Swarm nodes with traefik reverse proxy

To get started with Traefik, run the below command to create an overlay network to use for the services.
```
$ docker network create --driver=overlay traefik-net
```

Inspecting the network using `docker network inspect traefik-net` will show no containers as we haven't configured any container to use this network.

Run the below command to attach this network to new service
```
$ docker service create \
    --replicas 3 \
    --name whoami \
    --label traefik.port=8080 \
    --label traefik.docker.network=traefik-net \
    --network traefik-net \
    --publish 8080:8080 \
    narramadan/springboot-whoami
```

Run the below command to attach this network to already running service
```
$ docker service update \
    --label-add traefik.port=8080 \
    --label-add traefik.docker.network=traefik-net \
    --network-add traefik-net \
    whoami
```

* `--label traefik.port=8080` Specific traefik to connect to containers 8080 port
* `--label traefik.docker.network=traefik-net` Set the docker network to use for connections to this container. With swarm mode, ingress network is attached by default and we are attaching traefik-net to the container. If both these exists and `traefik.docker.network` not set will result in `Gateway Timeout` error.

### Deploy Traefik

Refer to detailed notes using [`docker-machine`](https://docs.traefik.io/user-guide/swarm-mode/)

For Traefik to work in swarm mode, Traefik should run on Manager node.

Run on the below command on either of the nodes. Setting `--constraint=node.role==manager` will ensure to have this service created on Manager node only.
```
$ docker service create \
    --name traefik \
    --constraint=node.role==manager \
    --publish 80:80 --publish 90:8080 \
    --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock \
    --network traefik-net \
    traefik \
    --docker \
    --docker.swarmmode \
    --docker.domain=traefik \
    --docker.watch \
    --logLevel=DEBUG \
    --web
```
* Port 80 and 90 are published on the cluster. 
* --web - Traefik webui is activated on port 90 (As we have WhoAmI port published on 8080, Traefik WebUI is set to port 90)
* --network traefik-net - The same network is attach the Traefik service and WhoAmI service
* --mount - mount the docker socket where Træfik is scheduled to be able to speak to the daemon.

Running `Docker service ls` will show two services started `whoamI` and `traefik`.

Run the below command to update logLevel for running traefik service
```
$ docker service update --args --logLevel=DEBUG traefik
```

### Testing WhoAmI with Traefik Reverse Proxy

Accessing Traefik dashboard will display 3 frontends & 1 backend created.

![Traefik Dashboard](/resources/traefik-dashboard.jpg?raw=true "Traefik Dashboard")

As observed for frontend pod, the rule is defined for Host header `Host:whoami.traefik`. This header should be set when we request traefik on manager node with 80 port.

On chrome browser, I added extension `ModHeader` and added request header Host = whoami.traefik. Accessing the application using ec2 public DNS http://ec2-xx-xx-xxx-xxx.ap-southeast-1.compute.amazonaws.com/ should display the container id next to 'Who Am I'.

Refreshing the browser will invoke each container in round-robin fashion and display respective container id. This was not the case when we relied on swarn default load balancing. We had to wait atleat 2-3 minutes for it to work.

![WhoAmI service Containers](/resources/whoami-service-containers.jpg?raw=true "WhoAmI service Containers")

![WhoAmiI Traefik Round-Robin](/resources/whoami-traefik-roundrobin.jpg?raw=true "WhoAmiI Traefik Round-Robin")

# Provisioning WhoAmI and Traefik services on Swarm cluster through Docker Componse file

We need to have a docker compose file which can provision both WhoAmI and Traefik services on the Swarm cluster. Supported version to use docker-compose.yml with swarm cluster should be `version: 3`

**Note**

Before we use docker compose to provision the services, we need to create our network manually. If we rely on docker-compose, network name created will have prefix appened with the name of the stack that we are deploying and thus WhoAmI & Traefik service link will not be established.

Run the below command to create the network if it is not available
```
$ docker network create --driver=overlay traefik-net
```

```yaml
version: '3'

# Create network with overlay driver
networks:
  traefik-net:
    external: true

# Define services to be deployed
services:
  traefik:
    image: traefik
    command: --docker --docker.swarmmode --docker.domain=traefik --docker.watch --logLevel=DEBUG --web
    deploy:      
      placement:
        constraints:
          - "node.role==manager"
    ports:
      - 80:80
      - 90:8080
      - 443:443
    volumes:
      #- /var/run/docker.sock:/var/run/docker.sock
      - type: bind
        source: /var/run/docker.sock
        target: /var/run/docker.sock
    networks:
      - traefik-net

  whoami:
    image: narramadan/springboot-whoami
    ports:
      - 8080:8080
    networks:
      - traefik-net
    deploy:
      replicas: 3
      labels:
        - traefik.port=8080
        - traefik.docker.network=whoamistack_traefik-net
```

Run the below command to test run the above docker-compose.yml
```
$ docker stack deploy --compose-file docker-compose.yml whoamistack

$ docker service ls
ID                  NAME                  MODE                REPLICAS            IMAGE                                 PORTS
qy7z5g91eeji        whoamistack_traefik   replicated          1/1                 traefik:latest                        *:80->80/tcp,*:90->8080/tcp,*:443->443/tcp
ngg15mam6bkt        whoamistack_whoami    replicated          3/3                 narramadan/springboot-whoami:latest   *:8080->8080/tcp
```

**Lets Encrypt Integration**
***TODO***

# Automating `Who Am I` with `Traefik Reverse Proxy` on `Docker Swarm` with `Terraform` & `Ansible`

Readme available under `automation` folder

# Backlog

Below are few this which are still work in progress. Have to pick these

1. Lets Encrypt Integration with Traefik
2. Make docker-compse.yml work to provision Traefik and WhoAmI service without hardcoding project name when running `docker stack deploy --compose-file.yml whoamistack`
3. Invoke docker-compose.yml from Ansible playbook to provision Traefik and WhoAmI service
4. EC2 Dynamic Inventory instead of hardcoding created manager and worker IP address in hosts file