[CmdletBinding()]
param(
    [string]$Region = 'us-east-2',
    [string]$ProjectName = 'bank-application',
    [string]$Environment = 'dev',
    [string]$StateBucketName
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

function Confirm-IamUser {
    param(
        [Parameter(Mandatory = $true)]
        [string]$UserName
    )

    $GetResult = Invoke-AwsCli -AwsArguments @('iam', 'get-user', '--user-name', $UserName)
    if ($GetResult.ExitCode -eq 0) {
        Write-Host "Usuario IAM encontrado: $UserName"
        return
    }

    $CreateResult = Invoke-AwsCli -AwsArguments @(
        'iam', 'create-user', '--user-name', $UserName,
        '--tags', "Key=Project,Value=$ProjectName", "Key=Environment,Value=$Environment"
    )
    Assert-AwsSucceeded -Result $CreateResult -FailureMessage "No fue posible crear el usuario IAM '$UserName'."
    Write-Host "Usuario IAM creado: $UserName"
}

function Set-InlinePolicy {
    param(
        [Parameter(Mandatory = $true)]
        [string]$UserName,

        [Parameter(Mandatory = $true)]
        [string]$PolicyName,

        [Parameter(Mandatory = $true)]
        [string]$PolicyDocument
    )

    $PolicyFile = [System.IO.Path]::GetTempFileName()
    try {
        $Utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($PolicyFile, $PolicyDocument, $Utf8WithoutBom)
        $PutResult = Invoke-AwsCli -AwsArguments @(
            'iam', 'put-user-policy', '--user-name', $UserName,
            '--policy-name', $PolicyName, '--policy-document', "file://$PolicyFile"
        )
    } finally {
        Remove-Item -LiteralPath $PolicyFile -Force -ErrorAction SilentlyContinue
    }
    Assert-AwsSucceeded -Result $PutResult `
        -FailureMessage "No fue posible asignar la política inline '$PolicyName' al usuario '$UserName'."
    Write-Host "Política inline aplicada: $UserName/$PolicyName"
}

function Remove-LegacyManagedPolicy {
    param(
        [Parameter(Mandatory = $true)]
        [string]$UserName,

        [Parameter(Mandatory = $true)]
        [string]$PolicyName,

        [Parameter(Mandatory = $true)]
        [string]$AccountId
    )

    $PolicyArn = "arn:aws:iam::$AccountId`:policy/$PolicyName"
    $GetResult = Invoke-AwsCli -AwsArguments @('iam', 'get-policy', '--policy-arn', $PolicyArn)
    if ($GetResult.ExitCode -ne 0) {
        return
    }

    $DetachResult = Invoke-AwsCli -AwsArguments @(
        'iam', 'detach-user-policy', '--user-name', $UserName, '--policy-arn', $PolicyArn
    )
    Assert-AwsSucceeded -Result $DetachResult `
        -FailureMessage "No fue posible retirar la política administrada anterior '$PolicyName'."

    $VersionsResult = Invoke-AwsCli -AwsArguments @(
        'iam', 'list-policy-versions', '--policy-arn', $PolicyArn, '--output', 'json'
    )
    Assert-AwsSucceeded -Result $VersionsResult `
        -FailureMessage "No fue posible consultar las versiones de la política anterior '$PolicyName'."

    @((ConvertFrom-Json $VersionsResult.Output).Versions) |
        Where-Object { -not $_.IsDefaultVersion } |
        ForEach-Object {
            $DeleteVersionResult = Invoke-AwsCli -AwsArguments @(
                'iam', 'delete-policy-version', '--policy-arn', $PolicyArn,
                '--version-id', $_.VersionId
            )
            Assert-AwsSucceeded -Result $DeleteVersionResult `
                -FailureMessage "No fue posible eliminar la versión '$($_.VersionId)' de '$PolicyName'."
        }

    $DeletePolicyResult = Invoke-AwsCli -AwsArguments @('iam', 'delete-policy', '--policy-arn', $PolicyArn)
    Assert-AwsSucceeded -Result $DeletePolicyResult `
        -FailureMessage "No fue posible eliminar la política administrada anterior '$PolicyName'."
    Write-Host "Política administrada anterior eliminada: $PolicyName"
}

$IdentityResult = Invoke-AwsCli -AwsArguments @(
    'sts', 'get-caller-identity', '--query', 'Account', '--output', 'text'
)
Assert-AwsSucceeded -Result $IdentityResult -FailureMessage 'No fue posible consultar la identidad de AWS.'
$AccountId = $IdentityResult.Output

if ([string]::IsNullOrWhiteSpace($StateBucketName)) {
    $StateBucketName = "$ProjectName-terraform-state-$AccountId"
}

$NamePrefix = "$ProjectName-$Environment"
$TerraformUserName = "$NamePrefix-github-terraform"
$DeployUserName = "$NamePrefix-github-deploy"
$TerraformPolicyName = "$TerraformUserName-policy"
$DeployPolicyName = "$DeployUserName-policy"
$StateKey = "$ProjectName/$Environment/terraform.tfstate"
$StateBucketArn = "arn:aws:s3:::$StateBucketName"

$TerraformPolicy = [ordered]@{
    Version   = '2012-10-17'
    Statement = @(
        [ordered]@{
            Sid      = 'TerraformStateBucket'
            Effect   = 'Allow'
            Action   = @('s3:GetBucketLocation', 's3:ListBucket')
            Resource = $StateBucketArn
        },
        [ordered]@{
            Sid      = 'TerraformStateObjects'
            Effect   = 'Allow'
            Action   = @('s3:GetObject', 's3:PutObject', 's3:DeleteObject')
            Resource = @("$StateBucketArn/$StateKey", "$StateBucketArn/$StateKey.tflock")
        },
        [ordered]@{
            Sid    = 'ApplicationInfrastructure'
            Effect = 'Allow'
            Action = @(
                'ec2:*', 'elasticloadbalancing:*', 'ecs:*', 'ecr:*', 'rds:*',
                'secretsmanager:*', 'logs:*', 'cloudwatch:*', 'ssm:GetParameter',
                'iam:GetRole', 'iam:CreateRole', 'iam:DeleteRole', 'iam:TagRole',
                'iam:UntagRole', 'iam:ListRoleTags', 'iam:UpdateRole',
                'iam:UpdateAssumeRolePolicy', 'iam:PutRolePolicy', 'iam:GetRolePolicy',
                'iam:DeleteRolePolicy', 'iam:AttachRolePolicy', 'iam:DetachRolePolicy',
                'iam:ListAttachedRolePolicies', 'iam:ListRolePolicies',
                'iam:CreateInstanceProfile', 'iam:DeleteInstanceProfile',
                'iam:AddRoleToInstanceProfile', 'iam:RemoveRoleFromInstanceProfile',
                'iam:GetInstanceProfile', 'iam:TagInstanceProfile',
                'iam:UntagInstanceProfile', 'iam:ListInstanceProfilesForRole',
                'iam:ListInstanceProfiles', 'iam:CreateServiceLinkedRole', 'budgets:*'
            )
            Resource = '*'
        },
        [ordered]@{
            Sid      = 'PassApplicationRoles'
            Effect   = 'Allow'
            Action   = 'iam:PassRole'
            Resource = @(
                "arn:aws:iam::$AccountId`:role/$NamePrefix-ecs-execution",
                "arn:aws:iam::$AccountId`:role/$NamePrefix-access",
                "arn:aws:iam::$AccountId`:role/$NamePrefix-*-task"
            )
        }
    )
} | ConvertTo-Json -Depth 10 -Compress

$DeployPolicy = [ordered]@{
    Version   = '2012-10-17'
    Statement = @(
        [ordered]@{
            Sid      = 'EcrLogin'
            Effect   = 'Allow'
            Action   = 'ecr:GetAuthorizationToken'
            Resource = '*'
        },
        [ordered]@{
            Sid    = 'PublishServiceImages'
            Effect = 'Allow'
            Action = @(
                'ecr:BatchCheckLayerAvailability', 'ecr:CompleteLayerUpload',
                'ecr:DescribeImages', 'ecr:GetDownloadUrlForLayer',
                'ecr:InitiateLayerUpload', 'ecr:PutImage', 'ecr:UploadLayerPart'
            )
            Resource = "arn:aws:ecr:$Region`:$AccountId`:repository/$NamePrefix-*"
        },
        [ordered]@{
            Sid      = 'DeployEcsServices'
            Effect   = 'Allow'
            Action   = @(
                'ecs:DescribeServices', 'ecs:DescribeTaskDefinition',
                'ecs:RegisterTaskDefinition', 'ecs:UpdateService'
            )
            Resource = '*'
        },
        [ordered]@{
            Sid      = 'PassEcsRoles'
            Effect   = 'Allow'
            Action   = 'iam:PassRole'
            Resource = @(
                "arn:aws:iam::$AccountId`:role/$NamePrefix-ecs-execution",
                "arn:aws:iam::$AccountId`:role/$NamePrefix-*-task"
            )
            Condition = [ordered]@{
                StringEquals = [ordered]@{
                    'iam:PassedToService' = 'ecs-tasks.amazonaws.com'
                }
            }
        }
    )
} | ConvertTo-Json -Depth 10 -Compress

Confirm-IamUser -UserName $TerraformUserName
Confirm-IamUser -UserName $DeployUserName

Set-InlinePolicy -UserName $TerraformUserName -PolicyName $TerraformPolicyName `
    -PolicyDocument $TerraformPolicy
Set-InlinePolicy -UserName $DeployUserName -PolicyName $DeployPolicyName `
    -PolicyDocument $DeployPolicy

Remove-LegacyManagedPolicy -UserName $TerraformUserName -PolicyName $TerraformPolicyName `
    -AccountId $AccountId
Remove-LegacyManagedPolicy -UserName $DeployUserName -PolicyName $DeployPolicyName `
    -AccountId $AccountId

Write-Host ''
Write-Host 'Usuarios y políticas de automatización preparados correctamente.'
Write-Host "Terraform: $TerraformUserName"
Write-Host "Despliegue: $DeployUserName"
Write-Host 'El script no creó access keys ni mostró credenciales.'
Write-Host 'Cree una access key para cada usuario desde IAM y guárdelas en los secrets del entorno development de GitHub.'
