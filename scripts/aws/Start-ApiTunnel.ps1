[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$AccessInstanceId,

    [Parameter(Mandatory)]
    [string]$LoadBalancerDnsName,

    [ValidateRange(1024, 65535)]
    [int]$LocalPort = 8080,

    [string]$Region = 'us-east-2'
)

$ErrorActionPreference = 'Stop'
$ParameterFile = New-TemporaryFile

try {
    $Parameters = @{
        host            = @($LoadBalancerDnsName)
        portNumber      = @('80')
        localPortNumber = @($LocalPort.ToString())
    } | ConvertTo-Json -Depth 3

    # Windows PowerShell 5.1 writes a BOM with -Encoding utf8. AWS CLI then
    # interprets those bytes as part of the first JSON property name.
    [System.IO.File]::WriteAllText(
        $ParameterFile.FullName,
        $Parameters,
        [System.Text.UTF8Encoding]::new($false)
    )

    Write-Host "El túnel mantendrá las APIs disponibles en http://localhost:$LocalPort"
    aws ssm start-session `
        --region $Region `
        --target $AccessInstanceId `
        --document-name AWS-StartPortForwardingSessionToRemoteHost `
        --parameters "file://$($ParameterFile.FullName)"

    if ($LASTEXITCODE -ne 0) {
        throw "El túnel de API terminó con código $LASTEXITCODE."
    }
} finally {
    Remove-Item -LiteralPath $ParameterFile.FullName -Force -ErrorAction SilentlyContinue
}
