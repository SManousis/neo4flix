[CmdletBinding()]
param([string] $EnvFile = '.env')

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-SmokeQuery([string] $Query) {
    # Credentials expand only inside the container, never in host arguments/output.
    $result = & docker @compose exec -T neo4j sh -c 'exec cypher-shell -u "$CYPHER_SHELL_USERNAME" -p "$CYPHER_SHELL_PASSWORD" --format plain "$1"' sh $Query
    if ($LASTEXITCODE -ne 0) { throw 'Neo4j smoke query failed.' }
    return @($result | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Get-SmokeUri([string] $Service) {
    $row = @($containers | Where-Object { $_.Service -eq $Service })
    if ($row.Count -ne 1 -or $row[0].State -ne 'running' -or $row[0].Health -ne 'healthy') {
        throw "$Service must be running and healthy."
    }
    $bindings = @($row[0].Publishers | Where-Object { $_.Protocol -eq 'tcp' -and $_.PublishedPort -gt 0 })
    if ($bindings.Count -ne 1 -or $bindings[0].URL -notin @('127.0.0.1', '0.0.0.0', '::', '::1')) {
        throw "$Service must have one local HTTP port binding."
    }
    return "http://localhost:$($bindings[0].PublishedPort)"
}

Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if (-not (Test-Path $EnvFile)) { throw 'Create .env from .env.example and replace local placeholders first.' }
    $compose = @('compose', '--env-file', $EnvFile, '-f', 'infra/compose.yml', '-f', 'infra/compose.dev.yml')
    $statusJson = & docker @compose ps --all --format json
    if ($LASTEXITCODE -ne 0) { throw 'Could not read Compose service status.' }
    # Compose versions emit either an array or one JSON object per line.
    $rawStatus = ($statusJson -join "`n").Trim()
    if (-not $rawStatus) { throw 'No Compose containers found.' }
    if ($rawStatus.StartsWith('[')) { $containers = @($rawStatus | ConvertFrom-Json) }
    else { $containers = @($statusJson | ForEach-Object { $_ | ConvertFrom-Json }) }
    $containers | Select-Object Service, State, Health, ExitCode | Format-Table
    $migrator = @($containers | Where-Object { $_.Service -eq 'database-migrator' })
    if ($migrator.Count -ne 1 -or $migrator[0].State -ne 'exited' -or $migrator[0].ExitCode -ne 0) {
        throw 'The database-migrator must have exited successfully.'
    }
    $logs = & docker @compose logs --no-color database-migrator
    if ($LASTEXITCODE -ne 0) { throw 'Could not read database-migrator logs.' }
    $logText = $logs -join "`n"
    if ($logText -notmatch 'GDS verification succeeded for database=neo4j version=2026\.07\.[0-9]+') {
        throw 'Migrator logs do not confirm the pinned GDS check.'
    }
    if ($logText -notmatch 'Database migrator completed mode=migrate database=neo4j versions=6(?:\s|$)') {
        throw 'Migrator logs do not confirm migration to latest (six versions).'
    }
    Write-Host 'Migrator exited 0; logs confirm GDS readiness and migration to latest (6 versions).'

    $constraints = Invoke-SmokeQuery "SHOW CONSTRAINTS YIELD name WHERE name IN ['user_id_unique','user_normalized_email_unique','movie_id_unique','genre_id_unique','genre_normalized_name_unique','rated_key_unique','watchlisted_key_unique','auth_session_id_unique','auth_challenge_id_unique','recommendation_share_id_unique','recommendation_share_token_hash_unique'] RETURN count(*) AS constraintCount;"
    if ($constraints.Count -ne 2 -or $constraints[0] -cne 'constraintCount' -or $constraints[1] -cne '11') {
        throw 'Expected all 11 named schema constraints.'
    }
    $indexes = Invoke-SmokeQuery "SHOW INDEXES YIELD name, state WHERE name IN ['movie_title_index','movie_release_year_index','genre_name_index'] AND state = 'ONLINE' RETURN count(*) AS onlineIndexCount;"
    if ($indexes.Count -ne 2 -or $indexes[0] -cne 'onlineIndexCount' -or $indexes[1] -cne '3') {
        throw 'Expected all 3 named schema indexes ONLINE.'
    }
    $gds = Invoke-SmokeQuery 'RETURN gds.version() AS version;'
    if ($gds.Count -ne 2 -or $gds[0] -cne 'version' -or $gds[1] -notmatch '^"2026\.07\.[0-9]+"$') {
        throw 'Live GDS version does not match the pinned 2026.07 release.'
    }
    Write-Host "Schema verified: 11 named constraints, 3 named ONLINE indexes; GDS=$($gds[1])."

    foreach ($service in @('user-service', 'movie-service', 'rating-service', 'recommendation-service')) {
        $uri = Get-SmokeUri $service
        $health = Invoke-RestMethod -Uri "$uri/actuator/health" -TimeoutSec 10
        if ($health.status -cne 'UP') { throw "$service is not UP." }
        Write-Host "$service is UP ($uri)."
    }
    $web = Invoke-WebRequest -Uri "$(Get-SmokeUri 'web')/" -UseBasicParsing -TimeoutSec 10
    if ($web.StatusCode -ne 200) { throw 'Web is not reachable.' }
    Write-Host 'Compose runtime smoke passed: migrations/schema verified, four services UP, GDS ready, web reachable.'
}
finally { Pop-Location }
