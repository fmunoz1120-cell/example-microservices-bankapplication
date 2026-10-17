data "aws_iam_policy_document" "ecs_task_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_execution" {
  name               = "${local.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "ecs_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [for secret in aws_secretsmanager_secret.database : secret.arn]
  }
}

resource "aws_iam_role_policy" "ecs_secrets" {
  name   = "database-secrets"
  role   = aws_iam_role.ecs_execution.id
  policy = data.aws_iam_policy_document.ecs_secrets.json
}

resource "aws_iam_role" "ecs_task" {
  for_each = local.services

  name               = "${local.name}-${each.key}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
}

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "access" {
  name               = "${local.name}-access"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "access_ssm" {
  role       = aws_iam_role.access.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "access" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = concat(
      [aws_db_instance.main.master_user_secret[0].secret_arn],
      [for secret in aws_secretsmanager_secret.database : secret.arn]
    )
  }

}

resource "aws_iam_role_policy" "access" {
  name   = "environment-bootstrap"
  role   = aws_iam_role.access.id
  policy = data.aws_iam_policy_document.access.json
}

resource "aws_iam_instance_profile" "access" {
  name = "${local.name}-access"
  role = aws_iam_role.access.name
}
