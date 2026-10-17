data "aws_ssm_parameter" "amazon_linux_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_key_pair" "access" {
  key_name   = "${local.name}-kafka-tunnel"
  public_key = trimspace(var.access_ssh_public_key)
}

resource "aws_instance" "access" {
  ami                         = data.aws_ssm_parameter.amazon_linux_arm64.value
  instance_type               = var.access_instance_type
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.access.id]
  iam_instance_profile        = aws_iam_instance_profile.access.name
  associate_public_ip_address = true
  private_ip                  = cidrhost(aws_subnet.public[0].cidr_block, 10)
  key_name                    = aws_key_pair.access.key_name

  user_data = templatefile("${path.module}/templates/access-host-user-data.sh.tftpl", {
    aws_region           = var.aws_region
    rds_host             = aws_db_instance.main.address
    admin_secret_arn     = aws_db_instance.main.master_user_secret[0].secret_arn
    account_secret_arn   = aws_secretsmanager_secret.database["account"].arn
    client_secret_arn    = aws_secretsmanager_secret.database["client"].arn
    corporate_secret_arn = aws_secretsmanager_secret.database["corporate"].arn
  })

  user_data_replace_on_change = true

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    encrypted   = true
    volume_type = "gp3"
    volume_size = 8
  }

  tags = { Name = "${local.name}-access" }
}
