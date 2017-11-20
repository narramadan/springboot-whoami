# Automating with `Terraform` & `Ansible` 

Sought inspiration for this section from [`here`](http://www.labouardy.com/setup-docker-swarm-on-aws-using-ansible-terraform/)

![Automation](/resources/automation.jpg?raw=true "Automation")

`Terraform` is a tool to provision infrastructure and `Ansible` is an agentless configuration management tool. To automate the deployment setup done under **Testing `Who Am I` with `Docker Swarm`** section, we mix these both tools to provision ec2 manager and node instances with docker installed on them and create Telerik and Who Am I services on Docker Swarm.

**Why not use Ansible to provision AWS instances ???**

Below is the general opinion view from few experts over different forums.

`Terraform` sets up Infrastructure. In AWS, you can also use Cloudformation for this. Terraform is IaC (Infrastructure as Code). For example, it will describe network topology, different AWS services, EC2 instance sizes, AMIs, VPCs, RDS instances, Lambda functions, etc.

`Ansible` configures servers. It's CM (Configuration Management). It defines what's on the servers. Mongo, Tomcat, Apache, etc. You can compare Ansible to Chef or Puppet

***Use both in tandem...***

# Setting up `Terraform` and `Ansible`
Both `Terraform` and `Ansible` can be installed and configured on developer machine. As I use Windows 10 PC, I prefer to have this setup run on docker ubuntu image.

Run the below commands to pull ubuntu image and run it with name `terraform-ansible`
```
// Pull Ubuntu image with sshd installed
PS C:\> docker pull rastasheep/ubuntu-sshd:16.04

// Run the image
PS C:\> docker run -d -P --name terraform-ansible rastasheep/ubuntu-sshd:16.04

// Pull the port on which 22 is exposed
PS C:\> docker port terraform-ansible 22

0.0.0.0:32768

// SSH to root@localhost on 32768 with password root
```

### Run the below commands to install Terraform
```
// Get updates and install unzip
$ apt-get update
$ apt-get -y install unzip
$ apt-get -y install vim

// create necessary folders
$ mkdir -p ~/opt/terraform
$ mkdir -p ~/Downloads

// Download and extract Terraform
$ cd ~/Downloads
$ wget https://releases.hashicorp.com/terraform/0.10.8/terraform_0.10.8_linux_amd64.zip

$ unzip terraform_0.10.8_linux_amd64.zip -d ~/opt/terraform

// Configure PATH environment variable by adding PATH configuration at end of file
$ vi ~/.bash_profile
export PATH=$PATH:~/opt/terraform

// Verify if terraform is installed correctly
$ terraform
```
Notes
> `~/opt` is a directory for installing unbundled packages, each one in its own subdirectory

### Run the below commands to install Ansible
```
$ apt-get update
$ apt-get -y install software-properties-common
$ apt-add-repository -y ppa:ansible/ansible
$ apt-get update
$ apt-get -y install ansible

// Use Python 3 by default
$ alias python=python3

// Verify if ansible is installed correctly
$ ansible
```

For Ansible to work with AWS, we need to install `boto3` through `PIP`. Run the below commands to install them
```
$ apt-get -y install python-pip

$ pip install boto
```
Notes
> PIP - Package management system to install & manage software packages written in python
> boto - AWS SDK for Python

## Setup EC2 Cluster using Terraform

To test run `Who Am I` in Docker Swarm, we need to spin up 3 ec2 instances of which one is Manager and rest Workers. We spin these up using Terraform with the below configuration files

### Generate Public & Private Key for AWS**
Create public & private SSh keys
```
$ mkdir ~/.ssh
$ chmod 700 ~/.ssh
$ ssh-keygen -t rsa

Generating public/private rsa key pair.
Enter file in which to save the key (/root/.ssh/id_rsa): /root/.ssh/terraform-ec2
Enter passphrase (empty for no passphrase):
Enter same passphrase again:
Your identification has been saved in /root/.ssh/terraform-ec2.
Your public key has been saved in /root/.ssh/terraform-ec2.pub.
The key fingerprint is:
SHA256:XXXXXXXXXXXXXXXXXXXXNHAo root@9da5f75a5a0b
```

### Terraform Scripts
> Terraform loads all .tf extension files in a directory when applying terraform.

1. variables.tf - Contains ec2 configurations like region name, instance type...

```
# Create instances in singapore region
variable "aws_region" {
  description = "AWS region on which we will setup the swarm cluster"
  default = "ap-southeast-1"
}

# Use Ubuntu Server 16.04
variable "ami" {
  description = "Ubuntu Server 16.04 LTS (HVM), SSD Volume Type"
  default = "ami-67a6e604"
}

# Use t2.micro
variable "instance_type" {
  description = "Instance type"
  default = "t2.micro"
}

# Use created terraform-ec2.pub file that is generated
variable "key_path" {
  description = "SSH Public Key path"
  default = "/root/.ssh/terraform-ec2.pub"
}

# Series of commands to execute to install docker
variable "bootstrap_path" {
  description = "Script to install Docker Engine"
  default = "install-docker.sh"
}
```

2. provider.tf - Configure AWS as Provider
```
provider "aws" {
  region = "${var.aws_region}"
}
```

3. secuirty_group.tf - Define security group to allow all traffic on Inbound/Outbound
```
resource "aws_security_group" "default" {
  name = "terraform-swarm-cluster"

  # Allow all inbound - Not recommended
  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Enable ICMP
  ingress {
    from_port = -1
    to_port = -1
    protocol = "icmp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
```
References
* `Ingress` - Incoming traffic | `Egress` - Outbound traffic
* [`ICMP Protocol`](https://en.wikipedia.org/wiki/Internet_Control_Message_Protocol)
* [`CIDR Blocks`](https://en.wikipedia.org/wiki/Classless_Inter-Domain_Routing)
* [`Security Group Best Practices`](https://www.stratoscale.com/blog/compute/aws-security-groups-5-best-practices/)

4. cluster-instances.tf - ec2 instances to spin for `Who Am I` testing
```
# Use the key pair file we created
resource "aws_key_pair" "default"{
  key_name = "terraform-swarm-keypair"
  public_key = "${file("${var.key_path}")}"
}

# Manager Node
resource "aws_instance" "manager" {
  ami = "${var.ami}"
  instance_type = "${var.instance_type}"
  key_name = "${aws_key_pair.default.id}"
  user_data = "${file("${var.bootstrap_path}")}"
  vpc_security_group_ids = ["${aws_security_group.default.id}"]

  tags {
    Name  = "swarm-manager"
  }
}

# Worker 1 Node
resource "aws_instance" "worker1" {
  ami = "${var.ami}"
  instance_type = "${var.instance_type}"
  key_name = "${aws_key_pair.default.id}"
  user_data = "${file("${var.bootstrap_path}")}"
  vpc_security_group_ids = ["${aws_security_group.default.id}"]

  tags {
    Name  = "swarm-manager1"
  }
}

# Worker 2 Node
resource "aws_instance" "worker2" {
  ami = "${var.ami}"
  instance_type = "${var.instance_type}"
  key_name = "${aws_key_pair.default.id}"
  user_data = "${file("${var.bootstrap_path}")}"
  vpc_security_group_ids = ["${aws_security_group.default.id}"]

  tags {
    Name  = "swarm-manager2"
  }
}
```

5. install-docker.sh - Bootstrap script to install latest version of Docker

### Apply Terraform to provision AWS instances**

Before applying terraform on AWS, we need to have valid AWS user account.

#### Create AWS IAM User Account 
* Login to `AWS Console` and choose `IAM` from Services
* Create a User with name `terraform-user` and provide `Programmatic access`. Move to `Permissions`
* Choose `Attach existing policies directly`. Search and select `AmazonEC2FullAccess`. Move to `Review`
* Review the details and create the user.
* Copy `Access key Id` and `Secret access key`. These should be set as Environmental Variables before applyinh terraform scripts.

```
export AWS_ACCESS_KEY_ID=XXXXX
export AWS_SECRET_ACCESS_KEY=XXXXXX
```

#### Verify Terraform scripts
Terraform needs to be initialized and download provider plugins. In this case it is `aws`.
```
$ terraform init
```

Verify the scripts plan before applying changes on AWS
```
$ terraform plan
```
Review the Instances, Security Group, Key Name etc...
**Gist** - https://gist.github.com/narramadan/eead55064f8aff890eecd39caa4b311e

#### Apply Terraform scripts
Apply the changes by issuing the below command.
```
$ terraform apply
```
Upon successful completion, login to AWS Console and verify instances spinned, Key Name & Security Group created.

## Prepare Ansible scripts to spin Docker Swarm with Traefik & Who Am I services
To ensure Ansible setup is working as expected, try to run few adhoc queries before proceeding with the setting up Ansible scripts to spin docker swarm on created cluster.

* `Setup SSH Agent for execution` - This will eliminate retyping password for SSH authetication each time we try to connect to our remote hosts
```
$ ssh-agent bash
$ ssh-add ~/.ssh/terraform-ec2.pem
```
> Provide phassphrase when promoted.

* `Add default configurations` - Set some default options globally in 'ansible.cfg' file. This file can be placed with the `same folder` from which ansible is being invoked or under `/etc/ansible/ansible.cfg` for system or user level or under `~/.ansible.cfg` file.
```
$ vi ansible.cfg

[defaults]
host_key_checking = False
```

* `Define Inventory file` - We need to connet to one or more machines to test run few adhoc commands. Lets define a static inventory file with public IPs of the cluster nodes that are provisioned through Terraform on AWS. Add them to hosts in a folder from which ansible is being invoked and add the text as specified.
```
$ vi hosts

[manager]
xxx.221.xxx.126

[workers]
54.xxx.147.xxx
54.251.188.xxx

[all:vars]
ansible_python_interpreter=/usr/bin/python3
```
> As there are more python instances installed on my machine, ansible is unable to predict which one to choose for execution. Setting `ansible_python_interpreter` will consider the specific python to use when executing ansible commands.

* `Execute Adhoc Commands on your nodes` - Run the below query to execute some adhoc commands on your nodes that are configured in host file.
```
$ ansible -i hosts all -m ping -u ubuntu

xxx.221.xxx.126 | SUCCESS => {
    "changed": false,
    "failed": false,
    "ping": "pong"
}
54.xxx.147.xxx | SUCCESS => {
    "changed": false,
    "failed": false,
    "ping": "pong"
}
54.251.188.xxx | SUCCESS => {
    "changed": false,
    "failed": false,
    "ping": "pong"
}

$ ansible -i hosts manager -m ping -u ubuntu
xxx.221.xxx.126 | SUCCESS => {
    "changed": false,
    "failed": false,
    "ping": "pong"
}
```
> Use option `-u ubuntu`. If username is not provided, then the username of the current session of your machine will be used for SSH execution

```
$  ansible -i hosts -u ubuntu all -a "/bin/echo hello"

xxx.221.xxx.126 | SUCCESS | rc=0 >>
hello

54.xxx.147.xxx | SUCCESS | rc=0 >>
hello

54.251.188.xxx | SUCCESS | rc=0 >>
hello
```
:clap: It Works !!!

Few more adhoc commands before we proceed further..
```
// Identify text window's capabilities from $TERM env variable
$ ansible -i hosts manager -m shell -a 'echo $TERM' -u ubuntu

// Copy file from local server to remote server
$ ansible -i hosts manager -m file -a"src=ec2.py dest=~" -u ubuntu
xxx.221.xxx.126 | SUCCESS => {
    "changed": true,
    "failed": false,
    "gid": 1000,
    "group": "ubuntu",
    "mode": "0775",
    "owner": "ubuntu",
    "path": "/home/ubuntu/ec2.py",
    "size": 4096,
    "state": "directory",
    "uid": 1000
}

// Gathering facts
$ ansible -i hosts manager -m setup -u ubuntu
Will return complete setup details in json format - [`Gist`](https://gist.github.com/narramadan/84e3bbbaba03f00b07174f6e382876e5)

-i inventory file to use
-m module name
-a module argunments
-u username
```
Refer [`here`](http://docs.ansible.com/ansible/latest/intro_adhoc.html#introduction-to-ad-hoc-commands) for more Adhoc Command capabilities.

### Support for Windows
Ansible manages Linux/Unix machines using SSH. With recent versions of Ansible, it supports managing Windows machines using PowerShell Remoting.

Refer [`here`](http://docs.ansible.com/ansible/latest/intro_windows.html) for more information.

### Configuring Docker Swarm on EC2 with Ansible Static Inventory
> Files for this section are available under `.\automation\static-inventory`

Gather the public IPs of Manager and Worker instances that are initialized via terraform. Have them defined in `hosts` file for Ansible to spin docker swarm based on defined playbook. 
```
[manager]
54.XXX.159.XXX

[workers]
52.XXX.249.XXX
XXX.254.XXX.136
```
> This is the manual step which should be elimated. Have to come up with dynamic inventory.

Now its time to define our ansible `playbook.yml` which will initialize docker swarm cluster using the nodes that we registered in `hosts` file.

```yml
---

  # Initialize Swarm on Manager node and get the join-token.
  - name: Init Swarm Manager
    hosts: manager
    gather_facts: False
    remote_user: ubuntu
    become: true
    become_method: sudo
    tasks:
      - name: Swarm Init
        command: docker swarm init --advertise-addr {{ inventory_hostname }}
        ignore_errors: yes

      - name: Get Worker Token
        command: docker swarm join-token worker -q
        register: worker_token

      - name: Show Worker Token
        debug: var=worker_token.stdout

      - name: Manager Token
        command: docker swarm join-token manager -q
        register: manager_token

      - name: Show Manager Token
        debug: var=manager_token.stdout

      - name: Create Network
        command: docker network create --driver=overlay traefik-net
        ignore_errors: yes

  # Attach worker nodes using the join-token retrieved from manager node
  - name: Join Swarm Cluster
    hosts: workers
    remote_user: ubuntu
    gather_facts: False
    become: true
    become_method: sudo
    vars:
      token: "{{ hostvars[groups['manager'][0]]['worker_token']['stdout'] }}"
      manager: "{{ hostvars[groups['manager'][0]]['inventory_hostname'] }}"
    tasks:
      - name: Join Swarm Cluster as a Worker
        command: docker swarm join --token {{ token }} {{ manager }}:2377
        register: worker
        ignore_errors: yes

      - name: Show Results
        debug: var=worker.stdout

      - name: Show Errors
        debug: var=worker.stderr
```

Verify the syntax
```
$ ansible-playbook -i hosts --syntax-check playbook.yml
```

Run the below command to execute the playbook with the configured hosts.
```
$ ansible-playbook -i hosts playbook.yml

You should see output with cows rendered using Cowsay along with debug messages written for variables and with final displaying the host IPs with their status

54.XXX.159.XXX              : ok=3    changed=1    unreachable=0    failed=0
52.XXX.249.XXX              : ok=5    changed=3    unreachable=0    failed=0
XXX.254.XXX.136             : ok=3    changed=1    unreachable=0    failed=0
```

Connect to Manager instance and run the below command to verify if the swarm cluster is created with one node as leader and other two as workers
```
$ docker node ls

ID                            HOSTNAME            STATUS              AVAILABILITY        MANAGER STATUS
nnwred6096ii7flgonbhbe4r3     ip-172-31-16-250    Ready               Active
jrs0xm6p0x2unn7pkmh86i04k     ip-172-31-22-39     Ready               Active
w91hbw2a1jg8igjq0jeyoilw0 *   ip-172-31-30-69     Ready               Active              Leader
```
#### Deploy WhoAmI and Traefik services on Swarm Cluster using Ansible
Copy the below lines to `playbook.yml` to include service deploy task and use `docker-compose.yml` to provision WhoAmI and Traefik services

```yml
# Run docker service commands to start traefik and whoami services on swarm cluster
  - name: Deploy Services
    hosts: manager
    remote_user: ubuntu
    gather_facts: False
    become: true
    become_method: sudo
    tasks:
      - name: Deploy Traefik Service
        command: docker service create \
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

      - name: Deploy WhoAmI Service
        command: docker service create \
                  --replicas 3 \
                  --name whoami \
                  --label traefik.port=8080 \
                  --label traefik.docker.network=traefik-net \
                  --network traefik-net \
                  --publish 8080:8080 \
                  narramadan/springboot-whoami
```

Run the below command to execute the playbook with the configured hosts and service provisioning
```
$ ansible-playbook -i hosts playbook.yml
```

**TODO** - Only manager node whoami container is working through Traefik. Getting `Gateway Error` for worknode whoami containers

### Configuring Dynamic Inventory with EC2
We need to setup dynamic inventory instead of popuating public IPs of spinned up ec2 instances in static inventory file as we create , stop and terminate instances as per our requirement.

For Ansible to work with EC2 to setup dynamic inventory, we need to use `AWS EC2 external inventory script`. Refer [`here`](http://docs.ansible.com/ansible/latest/intro_dynamic_inventory.html) for more details.
```
$ wget https://raw.githubusercontent.com/ansible/ansible/devel/contrib/inventory/ec2.py
$ wget https://raw.githubusercontent.com/ansible/ansible/devel/contrib/inventory/ec2.ini
```

These scripts when executed will invoke API calls to AWS and pull entire EC2 inventory details across all regions. To limit only to specific region, replace `all` with `ap-southeast-1` for key `regions` in `ec2.ini` file

Test the script if the configuration is correct
```
// Make the python script executable
$ chmod +x ec2.py

$ ./ec2.py --list

Outputs JSON with full inventory list available on EC2

// ec2.py will cache results to avoid repeated API calls. To clear cache run below command
$ ./ec2.py --refresh-cache
```
If you receive below error, uncomment `rds = False` and `elasticache = False` in ec2.ini file. Reason unknown :worried:
```
ERROR: "Forbidden", while: getting ElastiCache clustersroot@9da5f75a5a0b:~/ansible-test#
```

***TODO*** EC2 Dynamic Inventory


## Testing WhoAmI with Traefik Reverse Proxy
***TODO***

## Cleaning up the Instances after Test
Upon completing the test, clean up the instances by running the below command
```
$ terraform destroy
```
Terraform will terminate spinned ec2 instances, Storage Groups,Key Pairs and other associations as per plan defined

**Gist** - https://gist.github.com/narramadan/2d623a5a2d322573fc9e42f1bb2014ea

# Backlog

**Notes - When using Ansible with docker-service plugins**
Before invoking the playbook.yml, we need to install `docker.py` and 'docker-compose.py`
```
$ pip install 'docker-py>=1.7.0'

$ pip install 'docker-compose>=1.7.0'
```