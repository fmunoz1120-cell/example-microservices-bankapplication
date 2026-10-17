output "access_instance_id" {
  description = "Instancia administrada con SSM para inicialización y túneles."
  value       = aws_instance.access.id
}

output "internal_alb_dns_name" {
  description = "DNS privado del balanceador interno."
  value       = aws_lb.internal.dns_name
}

output "rds_endpoint" {
  description = "Endpoint privado de PostgreSQL."
  value       = aws_db_instance.main.address
}

output "kafka_tunnel_endpoint" {
  description = "Endpoint privado que las tareas ECS usan para alcanzar el Kafka local mediante el túnel inverso."
  value       = "${aws_instance.access.private_ip}:19092"
}

output "ecr_repository_urls" {
  description = "Repositorios de imágenes por microservicio."
  value       = { for name, repository in aws_ecr_repository.service : name => repository.repository_url }
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}
