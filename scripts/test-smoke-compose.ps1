$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Exercise the real smoke script with controlled Docker/HTTP boundaries. These
# contract cases are not live acceptance evidence; live Compose is run separately.
function Test-Path { param($Path) return $true }
function Invoke-RestMethod {
    param($Uri, $TimeoutSec)
    if ($Uri -cne 'http://localhost:49152/actuator/health') { throw 'Smoke did not discover the published service port.' }
    return @{ status = 'UP' }
}
function Invoke-WebRequest {
    param($Uri, [switch]$UseBasicParsing, $TimeoutSec)
    if ($Uri -cne 'http://localhost:49152/') { throw 'Smoke did not discover the published web port.' }
    return @{ StatusCode = 200 }
}
function docker {
    $global:LASTEXITCODE = 0
    if ($args -contains 'ps') {
        $rows = @(@{ Service = 'database-migrator'; State = 'exited'; Health = ''; ExitCode = $global:smokeCase.Exit })
        foreach ($name in @('neo4j', 'user-service', 'movie-service', 'rating-service', 'recommendation-service', 'web')) {
            $rows += @{ Service = $name; State = 'running'; Health = 'healthy'; ExitCode = 0
                Publishers = @(@{ URL = '127.0.0.1'; TargetPort = 18080; PublishedPort = 49152; Protocol = 'tcp' }) }
        }
        return ConvertTo-Json -Depth 5 -InputObject $rows
    }
    if ($args -contains 'logs') { return $global:smokeLogs }
    if ($args -contains 'exec') {
        if ($global:smokeCase.QueryFailure) { $global:LASTEXITCODE = 1; return 'query failed' }
        $query = [string]$args[-1]
        if ($query -match 'SHOW CONSTRAINTS') { return @('constraintCount', "$($global:smokeCase.Constraints)") }
        if ($query -match 'SHOW INDEXES') { return @('onlineIndexCount', "$($global:smokeCase.Indexes)") }
        if ($query -match 'gds.version') { return @('version', '"2026.07.0"') }
        throw "Unexpected query: $query"
    }
    throw "Unexpected Docker operation: $args"
}

$cases = @(
    @{ Name = 'missing migration completion'; Reject = $true; Completion = $false; Gds = $true; Constraints = 11; Indexes = 3; Exit = 0; QueryFailure = $false },
    @{ Name = 'missing GDS readiness'; Reject = $true; Completion = $true; Gds = $false; Constraints = 11; Indexes = 3; Exit = 0; QueryFailure = $false },
    @{ Name = 'migrator nonzero exit'; Reject = $true; Completion = $true; Gds = $true; Constraints = 11; Indexes = 3; Exit = 1; QueryFailure = $false },
    @{ Name = 'missing named constraint'; Reject = $true; Completion = $true; Gds = $true; Constraints = 10; Indexes = 3; Exit = 0; QueryFailure = $false },
    @{ Name = 'missing or offline named index'; Reject = $true; Completion = $true; Gds = $true; Constraints = 11; Indexes = 2; Exit = 0; QueryFailure = $false },
    @{ Name = 'failed schema query'; Reject = $true; Completion = $true; Gds = $true; Constraints = 11; Indexes = 3; Exit = 0; QueryFailure = $true },
    @{ Name = 'current migrator and complete schema'; Reject = $false; Completion = $true; Gds = $true; Constraints = 11; Indexes = 3; Exit = 0; QueryFailure = $false }
)
foreach ($case in $cases) {
    $global:smokeLogs = @()
    if ($case.Gds) {
        $global:smokeLogs += 'GDS verification succeeded for database=neo4j version=2026.07.0'
        # The legacy marker permits the original smoke to reach its false pass.
        if (-not $case.Completion) { $global:smokeLogs += 'GDS readiness check succeeded.' }
    }
    if ($case.Completion) { $global:smokeLogs += 'Database migrator completed mode=migrate database=neo4j versions=5' }
    $global:smokeCase = $case
    $rejected = $false
    try { & "$PSScriptRoot/smoke-compose.ps1" *> $null }
    catch {
        $rejected = $true
        $expectedError = switch ($case.Name) {
            'missing migration completion' { 'Migrator logs do not confirm migration to latest' }
            'missing GDS readiness' { 'Migrator logs do not confirm the pinned GDS check' }
            'migrator nonzero exit' { 'database-migrator must have exited successfully' }
            'missing named constraint' { 'Expected all 11 named schema constraints' }
            'missing or offline named index' { 'Expected all 3 named schema indexes ONLINE' }
            'failed schema query' { 'Neo4j smoke query failed' }
            default { 'NO ERROR EXPECTED' }
        }
        if ($_.Exception.Message -notlike "*$expectedError*") { throw }
        Write-Output "Observed: $($_.Exception.Message)"
    }
    if ($rejected -ne $case.Reject) { throw "Smoke contract failed: $($case.Name); rejected=$rejected expected=$($case.Reject)" }
    Write-Output "PASS: $($case.Name)"
}
