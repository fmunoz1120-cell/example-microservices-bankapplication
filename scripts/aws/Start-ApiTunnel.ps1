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
    @{
        host            = @($LoadBalancerDnsName)
        portNumber      = @('80')
        localPortNumber = @($LocalPort.ToString())
    } | ConvertTo-Json -Depth 3 | Set-Content -Path $ParameterFile -Encoding utf8

    Write-Host "El túnel mantendrá las APIs disponibles en http://localhost:$LocalPort"
    aws ssm start-session `
        --region $Region `
        --target $AccessInstanceId `
        --document-name AWS-StartPortForwardingSessionToRemoteHost `
        --parameters "file://$($ParameterFile.FullName)"
} finally {
    Remove-Item -LiteralPath $ParameterFile.FullName -Force -ErrorAction SilentlyContinue
}
