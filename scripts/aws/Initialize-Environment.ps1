[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$AccessInstanceId,

    [string]$Region = 'us-east-2'
)

$ErrorActionPreference = 'Stop'
$ParameterFile = New-TemporaryFile

function Invoke-AwsCli {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$AwsArguments
    )

    $PreviousErrorActionPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 convierte stderr de programas nativos en
        # NativeCommandError cuando ErrorActionPreference es Stop.
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

try {
    $ParameterJson = @{
        commands = @(
            'cloud-init status --wait',
            'sudo /usr/local/bin/bootstrap-bank-databases'
        )
    } | ConvertTo-Json -Depth 3
    $Utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($ParameterFile.FullName, $ParameterJson, $Utf8WithoutBom)

    $SendResult = Invoke-AwsCli -AwsArguments @(
        'ssm', 'send-command',
        '--region', $Region,
        '--instance-ids', $AccessInstanceId,
        '--document-name', 'AWS-RunShellScript',
        '--comment', 'Inicializar PostgreSQL para BankApplication',
        '--parameters', "file://$($ParameterFile.FullName)",
        '--query', 'Command.CommandId',
        '--output', 'text'
    )
    Assert-AwsSucceeded -Result $SendResult -FailureMessage 'No fue posible enviar el comando de inicialización.'
    $CommandId = $SendResult.Output

    $WaitResult = Invoke-AwsCli -AwsArguments @(
        'ssm', 'wait', 'command-executed',
        '--region', $Region,
        '--command-id', $CommandId,
        '--instance-id', $AccessInstanceId
    )
    $InvocationResult = Invoke-AwsCli -AwsArguments @(
        'ssm', 'get-command-invocation',
        '--region', $Region,
        '--command-id', $CommandId,
        '--instance-id', $AccessInstanceId,
        '--output', 'json'
    )
    Assert-AwsSucceeded -Result $InvocationResult `
        -FailureMessage "No fue posible consultar la inicialización SSM $CommandId."

    $Invocation = $InvocationResult.Output | ConvertFrom-Json
    Write-Host $Invocation.StandardOutputContent
    if (-not [string]::IsNullOrWhiteSpace($Invocation.StandardErrorContent)) {
        Write-Error $Invocation.StandardErrorContent -ErrorAction Continue
    }
    if ($Invocation.Status -ne 'Success') {
        $RemoteDetail = if ([string]::IsNullOrWhiteSpace($Invocation.StandardErrorContent)) {
            $Invocation.StatusDetails
        } else {
            $Invocation.StandardErrorContent.Trim()
        }
        throw "La inicialización SSM $CommandId terminó con estado $($Invocation.Status). Detalle remoto: $RemoteDetail"
    }
    if ($WaitResult.ExitCode -ne 0) {
        Write-Warning "El waiter de AWS terminó con código $($WaitResult.ExitCode), pero el comando SSM $CommandId confirmó estado Success."
    }
} finally {
    Remove-Item -LiteralPath $ParameterFile.FullName -Force -ErrorAction SilentlyContinue
}
