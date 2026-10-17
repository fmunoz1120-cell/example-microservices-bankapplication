[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$AccessInstanceId,

    [string]$Region = 'us-east-2'
)

$ErrorActionPreference = 'Stop'
$ParameterFile = New-TemporaryFile

try {
    @{
        commands = @(
            'cloud-init status --wait',
            'sudo /usr/local/bin/bootstrap-bank-databases'
        )
    } | ConvertTo-Json -Depth 3 | Set-Content -Path $ParameterFile -Encoding utf8

    $CommandId = aws ssm send-command `
        --region $Region `
        --instance-ids $AccessInstanceId `
        --document-name AWS-RunShellScript `
        --comment 'Inicializar PostgreSQL para BankApplication' `
        --parameters "file://$($ParameterFile.FullName)" `
        --query 'Command.CommandId' `
        --output text

    if ($LASTEXITCODE -ne 0) {
        throw 'No fue posible enviar el comando de inicialización.'
    }

    aws ssm wait command-executed --region $Region --command-id $CommandId --instance-id $AccessInstanceId 2>$null
    $InvocationJson = aws ssm get-command-invocation --region $Region --command-id $CommandId `
        --instance-id $AccessInstanceId --output json
    if ($LASTEXITCODE -ne 0) {
        throw "La inicialización de AWS falló. Revise el comando SSM $CommandId."
    }

    $Invocation = $InvocationJson | ConvertFrom-Json
    Write-Host $Invocation.StandardOutputContent
    if (-not [string]::IsNullOrWhiteSpace($Invocation.StandardErrorContent)) {
        Write-Error $Invocation.StandardErrorContent -ErrorAction Continue
    }
    if ($Invocation.Status -ne 'Success') {
        throw "La inicialización terminó con estado $($Invocation.Status). Revise el comando SSM $CommandId."
    }
} finally {
    Remove-Item -LiteralPath $ParameterFile.FullName -Force -ErrorAction SilentlyContinue
}
