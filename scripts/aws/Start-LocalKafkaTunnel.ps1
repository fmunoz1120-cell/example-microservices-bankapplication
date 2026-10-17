[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$AccessInstanceId,

    [Parameter(Mandatory)]
    [string]$IdentityFile,

    [ValidateRange(1024, 65535)]
    [int]$LocalKafkaPort = 29092,

    [ValidateRange(1024, 65535)]
    [int]$RemoteKafkaPort = 19092,

    [string]$Region = 'us-east-2'
)

$ErrorActionPreference = 'Stop'

foreach ($Command in @('aws', 'session-manager-plugin', 'ssh')) {
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "No se encontró '$Command' en PATH. Instálelo antes de abrir el túnel."
    }
}

$ResolvedIdentityFile = Resolve-Path -LiteralPath $IdentityFile -ErrorAction Stop
$ManagedStatus = aws ssm describe-instance-information `
    --region $Region `
    --filters "Key=InstanceIds,Values=$AccessInstanceId" `
    --query 'InstanceInformationList[0].PingStatus' `
    --output text

if ($LASTEXITCODE -ne 0 -or $ManagedStatus -ne 'Online') {
    throw "La instancia $AccessInstanceId no aparece Online en Systems Manager."
}

$Client = [System.Net.Sockets.TcpClient]::new()
try {
    $Connection = $Client.ConnectAsync('127.0.0.1', $LocalKafkaPort)
    if (-not $Connection.Wait([TimeSpan]::FromSeconds(3)) -or -not $Client.Connected) {
        throw "Kafka no está escuchando en localhost:$LocalKafkaPort."
    }
} catch {
    throw "Kafka no está escuchando en localhost:$LocalKafkaPort. Inicie el broker con el listener AWS antes del túnel."
} finally {
    $Client.Dispose()
}

$ProxyCommand = "aws ssm start-session --region $Region --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p"
$RemoteForward = '0.0.0.0:{0}:localhost:{1}' -f $RemoteKafkaPort, $LocalKafkaPort

Write-Host "Túnel Kafka activo mientras esta terminal permanezca abierta."
Write-Host "ECS:$RemoteKafkaPort -> localhost:$LocalKafkaPort"

& ssh `
    -i $ResolvedIdentityFile.Path `
    -N `
    -T `
    -o "ProxyCommand=$ProxyCommand" `
    -o 'IdentitiesOnly=yes' `
    -o 'ExitOnForwardFailure=yes' `
    -o 'ServerAliveInterval=30' `
    -o 'ServerAliveCountMax=3' `
    -o 'StrictHostKeyChecking=accept-new' `
    -R $RemoteForward `
    "ec2-user@$AccessInstanceId"

if ($LASTEXITCODE -ne 0) {
    throw "El túnel terminó con código $LASTEXITCODE. Compruebe que el puerto remoto $RemoteKafkaPort no esté ocupado."
}
