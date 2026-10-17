resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_cloudwatch_log_group" "service" {
  for_each = local.services

  name              = "/ecs/${local.name}/${each.key}"
  retention_in_days = 14
}

resource "aws_ecs_task_definition" "service" {
  for_each = local.services

  family                   = "${local.name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task[each.key].arn

  container_definitions = jsonencode([{
    name      = each.key
    image     = "${aws_ecr_repository.service[each.key].repository_url}:bootstrap"
    essential = true

    portMappings = [{
      containerPort = each.value.port
      hostPort      = each.value.port
      protocol      = "tcp"
    }]

    environment = concat(
      [
        { name = "SERVER_ADDRESS", value = "0.0.0.0" },
        { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${each.value.database}?sslmode=require" },
        { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=70" }
      ],
      each.value.kafka ? [
        { name = "KAFKA_BOOTSTRAP_SERVERS", value = "${aws_instance.access.private_ip}:19092" },
        { name = "CLIENT_EVENTS_TOPIC", value = "bank.clients.v1" }
      ] : []
    )

    secrets = [
      {
        name      = "DB_USER"
        valueFrom = "${aws_secretsmanager_secret.database[each.key].arn}:username::"
      },
      {
        name      = "DB_PASSWORD"
        valueFrom = "${aws_secretsmanager_secret.database[each.key].arn}:password::"
      }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service[each.key].name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])
}

resource "aws_ecs_service" "service" {
  for_each = local.services

  name            = "${local.name}-${each.key}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = 0
  launch_type     = "FARGATE"

  health_check_grace_period_seconds = 120
  enable_execute_command            = true

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.service[each.key].arn
    container_name   = each.key
    container_port   = each.value.port
  }

  depends_on = [aws_lb_listener_rule.service]

  lifecycle {
    ignore_changes = [desired_count, task_definition]
  }
}
