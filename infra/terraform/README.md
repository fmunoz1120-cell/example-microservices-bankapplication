# Infraestructura AWS de BankApplication

Este directorio crea el ambiente privado de desarrollo en `us-east-2`:

- ECS Fargate y ECR para `account`, `client` y `corporate`.
- Un ALB interno con enrutamiento por rutas.
- Un RDS PostgreSQL privado con tres bases y usuarios independientes.
- Una instancia Amazon Linux administrada por Systems Manager para inicialización, acceso a las APIs y el túnel inverso hacia Kafka local.
- Secrets Manager, CloudWatch y una alerta opcional de presupuesto.

Kafka y Kafdrop permanecen en la computadora local. `account` y `client` llegan al broker mediante un túnel SSH inverso sobre Session Manager. Kafka no se expone a Internet. El puerto `19092` de la instancia de acceso acepta conexiones únicamente desde el security group de ECS.

## Requisitos

- AWS CLI v2 autenticado en la cuenta de destino.
- Terraform 1.8 o superior.
- Session Manager plugin y OpenSSH Client disponibles en `PATH`.
- Docker Desktop para Kafka y Kafdrop locales.
- Permisos administrativos durante el primer `terraform apply`.
- Acceso a EC2, ECR, ECS, RDS, Secrets Manager y Systems Manager en `us-east-2`.
- Permisos IAM para crear dos usuarios de automatización y sus políticas inline.

## Clave dedicada para el túnel

Generar una clave exclusiva; la clave privada debe permanecer fuera del repositorio:

```powershell
ssh-keygen -t ed25519 -f "$HOME\.ssh\bank-kafka-tunnel"
Get-Content "$HOME\.ssh\bank-kafka-tunnel.pub"
```

Copiar la línea pública completa en `access_ssh_public_key` dentro de `terraform.tfvars`. GitHub Actions necesita la misma línea pública en la variable `ACCESS_SSH_PUBLIC_KEY`. La clave privada nunca se carga en GitHub ni en Terraform.

## Primer aprovisionamiento

Desde la raíz del repositorio:

```powershell
.\scripts\aws\New-TerraformState.ps1
.\scripts\aws\New-GitHubAwsUsers.ps1
Copy-Item infra\terraform\terraform.tfvars.example infra\terraform\terraform.tfvars
```

El segundo script crea los usuarios `bank-application-dev-github-terraform` y `bank-application-dev-github-deploy`, junto con políticas separadas. No crea access keys ni imprime credenciales. Editar `terraform.tfvars`, en especial `access_ssh_public_key` y `budget_email`. La retención RDS predeterminada es un día para ser compatible con cuentas del plan gratuito; `db_backup_retention_days` permite aumentarla hasta 35 en otros planes.

```powershell
terraform "-chdir=infra/terraform" init "-backend-config=backend.hcl"
terraform -chdir=infra/terraform fmt -check -recursive
terraform -chdir=infra/terraform validate
terraform -chdir=infra/terraform plan -out=tfplan
terraform -chdir=infra/terraform apply tfplan
```

El primer `apply` se realiza localmente con la sesión AWS del operador. Los usuarios de GitHub permanecen fuera del estado principal para que Terraform no pueda retirar sus propias credenciales durante una actualización o destrucción. Los servicios ECS se crean con capacidad deseada cero.

Guardar los resultados e inicializar las bases:

```powershell
$AccessInstanceId = terraform -chdir=infra/terraform output -raw access_instance_id
$AlbDnsName = terraform -chdir=infra/terraform output -raw internal_alb_dns_name
$KafkaTunnelEndpoint = terraform -chdir=infra/terraform output -raw kafka_tunnel_endpoint
.\scripts\aws\Initialize-Environment.ps1 -AccessInstanceId $AccessInstanceId
```

Flyway creará las tablas cuando arranquen por primera vez las aplicaciones.

## Kafka local y túnel inverso

El Compose de referencia está en `infra/local-kafka/compose.yml`. Tiene estos listeners:

- `INTERNAL://kafka:19092` para Kafdrop y contenedores locales.
- `HOST://localhost:9092` para aplicaciones y pruebas ejecutadas en Windows.
- `AWS://<ip-privada-access>:19092`, recibido localmente en `localhost:29092`, para ECS.

Usar la IP incluida en `kafka_tunnel_endpoint` y levantar Kafka:

```powershell
$env:AWS_KAFKA_ADVERTISED_HOST = ($KafkaTunnelEndpoint -split ':')[0]
docker compose -f infra/local-kafka/compose.yml up -d
```

Si ya existe otro Compose de Kafka, aplicar esos tres listeners en él antes de recrear el contenedor. Los puertos del host se enlazan solo a `127.0.0.1`; Kafdrop usa `kafka:19092` dentro de Docker.

Crear los tópicos de forma idempotente:

```powershell
foreach ($Topic in @('bank.clients.v1', 'bank.clients.v1.DLT', 'bank.clients.integration.v1', 'bank.clients.integration.v1.DLT')) {
  docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists --topic $Topic --partitions 3 --replication-factor 1
}
```

Abrir el túnel en una terminal y mantenerla activa:

```powershell
.\scripts\aws\Start-LocalKafkaTunnel.ps1 `
  -AccessInstanceId $AccessInstanceId `
  -IdentityFile "$HOME\.ssh\bank-kafka-tunnel"
```

El script verifica AWS CLI, Session Manager Plugin, OpenSSH, el estado SSM de la instancia y Kafka en `localhost:29092`. `ExitOnForwardFailure` impide continuar si `19092` ya está ocupado en la instancia.

Si la computadora o el túnel se detienen, `client` conserva el outbox pendiente y `account` reintenta la conexión. Al restablecer el túnel, el publicador y el consumidor retoman el procesamiento.

## GitHub Actions

Crear una access key para cada usuario desde **IAM → Users → Security credentials**. AWS muestra el secret una sola vez. Copiar cada par directamente a los secrets del environment protegido `development`; no guardarlos en el repositorio ni en archivos Terraform.

| Secret | Credencial |
| --- | --- |
| `AWS_TERRAFORM_ACCESS_KEY_ID` | Access key ID de `bank-application-dev-github-terraform` |
| `AWS_TERRAFORM_SECRET_ACCESS_KEY` | Secret access key de `bank-application-dev-github-terraform` |
| `AWS_DEPLOY_ACCESS_KEY_ID` | Access key ID de `bank-application-dev-github-deploy` |
| `AWS_DEPLOY_SECRET_ACCESS_KEY` | Secret access key de `bank-application-dev-github-deploy` |

Configurar además estas variables del environment:

| Variable | Valor |
| --- | --- |
| `TF_STATE_BUCKET` | Bucket creado por `New-TerraformState.ps1` |
| `AWS_BUDGET_EMAIL` | Correo para la alerta de presupuesto |
| `ACCESS_SSH_PUBLIC_KEY` | Contenido de `bank-kafka-tunnel.pub` |

No configurar `AWS_SESSION_TOKEN`: las claves pertenecen a usuarios IAM y no a la sesión temporal `AccountFullAccessRole`. Restringir el environment `development` a la rama `main` y, si el plan lo requiere, añadir aprobación para despliegues.

`CI` ejecuta Maven y PMD. `Terraform AWS` permite ejecutar `plan` o `apply`. `Deploy AWS` publica las imágenes y mantiene una tarea de cada servicio. La clave SSH privada nunca se carga en GitHub.

Para rotar una credencial, crear la segunda access key del usuario, actualizar los dos secrets correspondientes, ejecutar el workflow afectado y solo entonces desactivar y eliminar la clave anterior. IAM admite como máximo dos access keys por usuario.

### Alternativa recomendada cuando AWS permite OIDC

OIDC con `token.actions.githubusercontent.com` y `sts:AssumeRoleWithWebIdentity` evita claves permanentes y es la opción ideal. Este ambiente usa access keys porque una SCP de AWS Organizations bloquea los proveedores de identidad. Si la organización habilita OIDC, se debe volver a roles de corta duración, eliminar las access keys de GitHub y retirar los usuarios de automatización.

## Acceso privado a las APIs

Con el túnel Kafka activo y los servicios desplegados, abrir otra terminal:

```powershell
.\scripts\aws\Start-ApiTunnel.ps1 `
  -AccessInstanceId $AccessInstanceId `
  -LoadBalancerDnsName $AlbDnsName
```

Las APIs quedan disponibles en `http://localhost:8080`:

```powershell
Invoke-RestMethod http://localhost:8080/api/clients
Invoke-RestMethod http://localhost:8080/api/accounts
Invoke-RestMethod http://localhost:8080/api/catalog-types
```

## Orden de operación

1. Mantener Kafka y Kafdrop locales activos.
2. Abrir `Start-LocalKafkaTunnel.ps1`.
3. Desplegar o iniciar las tareas ECS.
4. Abrir `Start-ApiTunnel.ps1` para consumir las APIs.

Los logs se encuentran en `/ecs/bank-application-dev/<servicio>`. Antes de destruir el ambiente, detener los servicios y confirmar si debe conservarse el snapshot final de RDS.
