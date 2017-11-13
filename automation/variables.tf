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