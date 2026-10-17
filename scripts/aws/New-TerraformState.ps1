[CmdletBinding()]
param(
    [string]$Region = 'us-east-2',
    [string]$BucketName
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
    throw 'AWS CLI no está instalado o no está disponible en PATH.'
}

function Invoke-AwsCli {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$AwsArguments
    )

    $PreviousErrorActionPreference = $ErrorActionPreference
    try {
        # Windows PowerShell convierte stderr de programas nativos en NativeCommandError
        # cuando ErrorActionPreference es Stop. AWS CLI comunica también condiciones
        # esperadas, como un bucket inexistente, mediante stderr y el código de salida.
        $ErrorActionPreference = 'Continue'
        $CommandOutput = & aws @AwsArguments 2>&1
        $ExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $PreviousErrorActionPreference
    }

    [PSCustomObject]@{
        ExitCode = $ExitCode
        Output   = (($CommandOutput | Out-String).Trim())
    }
}

function Assert-AwsSucceeded {
    param(
        [Parameter(Mandatory = $true)]
        [PSCustomObject]$Result,

        [Parameter(Mandatory = $true)]
        [string]$FailureMessage
    )

    if ($Result.ExitCode -eq 0) {
        return
    }

    $Detail = if ([string]::IsNullOrWhiteSpace($Result.Output)) {
        'AWS CLI no devolvió detalles adicionales.'
    } else {
        $Result.Output
    }

    throw "$FailureMessage Código de salida: $($Result.ExitCode). Detalle: $Detail"
}

$IdentityResult = Invoke-AwsCli -AwsArguments @(
    'sts', 'get-caller-identity', '--query', 'Account', '--output', 'text'
)
Assert-AwsSucceeded -Result $IdentityResult -FailureMessage 'No fue posible consultar la identidad de AWS.'
$AccountId = $IdentityResult.Output

if ([string]::IsNullOrWhiteSpace($BucketName)) {
    $BucketName = "bank-application-terraform-state-$AccountId"
}

$HeadBucketResult = Invoke-AwsCli -AwsArguments @('s3api', 'head-bucket', '--bucket', $BucketName)
if ($HeadBucketResult.ExitCode -eq 0) {
    Write-Host "Bucket de estado encontrado: $BucketName"
} else {
    Write-Host "El bucket de estado no existe o no es accesible; se intentará crear: $BucketName"

    if ($Region -eq 'us-east-1') {
        $CreateBucketArguments = @('s3api', 'create-bucket', '--bucket', $BucketName, '--region', $Region)
    } else {
        $CreateBucketArguments = @(
            's3api', 'create-bucket', '--bucket', $BucketName, '--region', $Region,
            '--create-bucket-configuration', "LocationConstraint=$Region"
        )
    }

    $CreateBucketResult = Invoke-AwsCli -AwsArguments $CreateBucketArguments
    Assert-AwsSucceeded -Result $CreateBucketResult `
        -FailureMessage "No fue posible crear el bucket de estado '$BucketName'."
    Write-Host "Bucket de estado creado: $BucketName"
}

Write-Host 'Configurando bloqueo de acceso público...'
$PublicAccessResult = Invoke-AwsCli -AwsArguments @(
    's3api', 'put-public-access-block', '--bucket', $BucketName,
    '--public-access-block-configuration',
    'BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true'
)
Assert-AwsSucceeded -Result $PublicAccessResult `
    -FailureMessage "No fue posible bloquear el acceso público del bucket '$BucketName'."

Write-Host 'Habilitando versionado...'
$VersioningResult = Invoke-AwsCli -AwsArguments @(
    's3api', 'put-bucket-versioning', '--bucket', $BucketName,
    '--versioning-configuration', 'Status=Enabled'
)
Assert-AwsSucceeded -Result $VersioningResult `
    -FailureMessage "No fue posible habilitar el versionado del bucket '$BucketName'."

Write-Host 'Configurando cifrado AES-256...'
$EncryptionResult = Invoke-AwsCli -AwsArguments @(
    's3api', 'put-bucket-encryption', '--bucket', $BucketName,
    '--server-side-encryption-configuration',
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"},"BucketKeyEnabled":true}]}'
)
Assert-AwsSucceeded -Result $EncryptionResult `
    -FailureMessage "No fue posible configurar el cifrado del bucket '$BucketName'."

$BackendPath = Join-Path $PSScriptRoot '..\..\infra\terraform\backend.hcl'
@"
bucket       = "$BucketName"
key          = "bank-application/dev/terraform.tfstate"
region       = "$Region"
encrypt      = true
use_lockfile = true
"@ | Set-Content -Path $BackendPath -Encoding utf8

Write-Host "Bucket de estado configurado correctamente: $BucketName"
Write-Host "Configuración local creada en: $BackendPath"
