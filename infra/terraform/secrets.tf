resource "random_password" "database" {
  for_each = local.services

  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "database" {
  for_each = local.services

  name_prefix             = "${local.name}-${each.key}-db-"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "database" {
  for_each = local.services

  secret_id = aws_secretsmanager_secret.database[each.key].id
  secret_string = jsonencode({
    username = "bank_${each.key}"
    password = random_password.database[each.key].result
    dbname   = each.value.database
    host     = aws_db_instance.main.address
    port     = aws_db_instance.main.port
  })
}
