locals {
  name = "${var.project_name}-${var.environment}"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }

  services = {
    account = {
      port        = 8000
      cpu         = 512
      memory      = 1024
      database    = "bank_account"
      route_paths = ["/api/accounts*", "/api/transactions*"]
      priority    = 10
      kafka       = true
    }
    client = {
      port        = 8001
      cpu         = 512
      memory      = 1024
      database    = "bank_client"
      route_paths = ["/api/clients*"]
      priority    = 20
      kafka       = true
    }
    corporate = {
      port        = 8002
      cpu         = 256
      memory      = 512
      database    = "bank_corporate"
      route_paths = ["/api/catalog-types*", "/api/catalog-values*", "/api/work-areas*"]
      priority    = 30
      kafka       = false
    }
  }
}

data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}
