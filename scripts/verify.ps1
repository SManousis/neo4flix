param(
    [switch] $WhatIf,
    [switch] $TestOnly,
    [switch] $Integration
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repository = Split-Path $PSScriptRoot -Parent
$windows = $env:OS -eq 'Windows_NT'
$goal = if ($TestOnly) { 'test' } else { 'verify' }
$maven = if ($windows) { './mvnw.cmd' } else { 'sh' }
[string[]] $mavenArguments = if ($windows) { @($goal) } else { @('./mvnw', $goal) }
$integrationArguments = if ($windows) {
    @('-pl', 'backend/platform-common,backend/rating-service,backend/user-service', '-am', '-Dtest=Neo4jSchemaIntegrationTest,*ConcurrencyIT,AuthNeo4jIntegrationIT,AuthProductionContextIT', '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
} else {
    @('./mvnw', '-pl', 'backend/platform-common,backend/rating-service,backend/user-service', '-am', '-Dtest=Neo4jSchemaIntegrationTest,*ConcurrencyIT,AuthNeo4jIntegrationIT,AuthProductionContextIT', '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
}

if ($Integration) {
    $steps = @(@{ Directory = '.'; Command = $maven; Arguments = $integrationArguments })
} else {
    $steps = @(
        @{ Directory = '.'; Command = $maven; Arguments = $mavenArguments }
        @{ Directory = 'frontend'; Command = 'npm'; Arguments = @('ci') }
    )
    if (-not $TestOnly) {
        $steps += @{ Directory = 'frontend'; Command = 'npm'; Arguments = @('run', 'lint') }
    }
    $steps += @{ Directory = 'frontend'; Command = 'npm'; Arguments = @('test', '--', '--run') }
    if (-not $TestOnly) {
        $steps += @(
            @{ Directory = 'frontend'; Command = 'npm'; Arguments = @('run', 'build') }
            @{ Directory = '.'; Command = 'docker'; Arguments = @('compose', '--env-file', '.env.example', '-f', 'infra/compose.yml', 'config') }
        )
    }
    $steps += @{ Directory = '.'; Command = $maven; Arguments = $integrationArguments }
}

foreach ($step in $steps) {
    $arguments = $step.Arguments
    $description = "$($step.Command) $($arguments -join ' ')"
    Write-Output "[$($step.Directory)] $description"
    if ($WhatIf) { continue }

    Push-Location (Join-Path $repository $step.Directory)
    try {
        # Use the native npm launcher on Windows, independent of script policy.
        $command = if ($windows -and $step.Command -eq 'npm') { 'npm.cmd' } else { $step.Command }
        & $command @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$description failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}
