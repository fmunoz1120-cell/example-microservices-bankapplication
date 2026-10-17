variable "aws_region" {
  description = "Región de AWS donde se despliega el ambiente."
  type        = string
  default     = "us-east-2"
}

variable "project_name" {
  description = "Nombre base de los recursos."
  type        = string
  default     = "bank-application"
}

variable "environment" {
  description = "Nombre corto del ambiente."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "Bloque CIDR de la VPC."
  type        = string
  default     = "10.30.0.0/16"
}

variable "db_instance_class" {
  description = "Clase de la instancia RDS."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_engine_version" {
  description = "Versión de PostgreSQL soportada por RDS; null selecciona la predeterminada vigente."
  type        = string
  default     = null
  nullable    = true
}

variable "db_backup_retention_days" {
  description = "Días de retención de backups automáticos de RDS."
  type        = number
  default     = 1

  validation {
    condition     = var.db_backup_retention_days >= 0 && var.db_backup_retention_days <= 35
    error_message = "db_backup_retention_days debe estar entre 0 y 35."
  }
}

variable "access_instance_type" {
  description = "Tipo de instancia del nodo de acceso administrado con SSM."
  type        = string
  default     = "t4g.micro"
}

variable "access_ssh_public_key" {
  description = "Clave pública SSH dedicada para abrir el túnel inverso de Kafka mediante SSM."
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)) ", trimspace(var.access_ssh_public_key)))
    error_message = "access_ssh_public_key debe contener una clave pública OpenSSH válida."
  }
}

variable "monthly_budget_usd" {
  description = "Presupuesto mensual en USD; use 0 para no crear la alerta."
  type        = number
  default     = 200

  validation {
    condition     = var.monthly_budget_usd >= 0
    error_message = "monthly_budget_usd no puede ser negativo."
  }
}

variable "budget_email" {
  description = "Correo que recibe la alerta al consumir 80 %; vacío deshabilita la alerta."
  type        = string
  default     = ""
}
