resource "aws_security_group" "access" {
  name        = "${local.name}-access"
  description = "Nodo SSM y extremo privado del tunel Kafka"
  vpc_id      = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_vpc_security_group_ingress_rule" "access_kafka_from_ecs" {
  security_group_id            = aws_security_group.access.id
  referenced_security_group_id = aws_security_group.ecs.id
  description                  = "Kafka local publicado por el tunel SSH inverso"
  from_port                    = 19092
  to_port                      = 19092
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "ALB interno accesible solo desde el nodo SSM"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.access.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "ecs" {
  name        = "${local.name}-ecs"
  description = "Tareas ECS accesibles solo desde el ALB"
  vpc_id      = aws_vpc.main.id

  dynamic "ingress" {
    for_each = local.services
    content {
      from_port       = ingress.value.port
      to_port         = ingress.value.port
      protocol        = "tcp"
      security_groups = [aws_security_group.alb.id]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "rds" {
  name        = "${local.name}-rds"
  description = "PostgreSQL para ECS y administracion SSM"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id, aws_security_group.access.id]
  }
}

