# Automating `Who Am I` with `Traefik Reverse Proxy` on `Docker Swarm` with `Terraform` & `Ansible`

Sought inspiration for this section from [`here`](http://www.labouardy.com/setup-docker-swarm-on-aws-using-ansible-terraform/)

![Automation](/resources/automation.jpg?raw=true "Automation")

`Terraform` is a tool to provision infrastructure and `Ansible` is an agentless configuration management tool. To automate the deployment setup done under **Testing `Who Am I` with `Docker Swarm`** section, we mix these both tools to provision ec2 manager and node instances with docker installed on them and create Telerik and Who Am I services on Docker Swarm.

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

Run the below commands to install Terraform
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

Run the below commands to install Ansible
```
$ apt-get update
$ apt-get -y install software-properties-common
$ apt-add-repository -y ppa:ansible/ansible
$ apt-get update
$ apt-get -y install ansible

// Verify if ansible is installed correctly
$ ansible
```

## Setup EC2 Cluster using Terraform

To test run `Who Am I` in Docker Swarm, we need to spin up 3 ec2 instances of which one is Manager and rest Workers. We spin these up using Terraform with the below configuration files

###Generate Public & Private Key for AWS**
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
<script src="https://gist.github.com/narramadan/eead55064f8aff890eecd39caa4b311e.js"></script>

#### Apply Terraform scripts
Apply the changes by issuing the below command.
```
$ terraform apply
```
Upon successful completion, login to AWS Console and verify instances spinned, Key Name & Security Group created.

## Prepare Ansible scripts to spin Docker Swarm with Traefik & Who Am I services

## Testing WhoAmI with Traefik Reverse Proxy

## Cleaning up the Instances after Test
Upon completing the test, clean up the instances by running the below command
```
$ terraform destroy
```
Terraform will terminate spinned ec2 instances, Storage Groups,Key Pairs and other associations as per plan defined

<script src="https://gist.github.com/narramadan/2d623a5a2d322573fc9e42f1bb2014ea.js"></script>