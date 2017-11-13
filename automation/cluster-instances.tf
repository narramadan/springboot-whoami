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
    Name  = "swarm-worker1"
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
    Name  = "swarm-worker2"
  }
}