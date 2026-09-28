$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-Contract([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    Assert-Contract (Test-Path 'infra/compose.yml') 'infra/compose.yml is missing.'
    $json = & docker compose --env-file .env.example -f infra/compose.yml config --format json
    Assert-Contract ($LASTEXITCODE -eq 0) 'Docker Compose configuration failed.'
    $config = ($json -join "`n") | ConvertFrom-Json
    $expected = @('neo4j', 'database-migrator', 'user-service', 'movie-service', 'rating-service', 'recommendation-service', 'web')
    $actual = @($config.services.PSObject.Properties.Name)
    Assert-Contract (@(Compare-Object $expected $actual).Count -eq 0) 'Unexpected Compose services.'
    Assert-Contract ($config.services.neo4j.image -ceq 'neo4j:2026.07.1-community') 'Neo4j image must match the baseline pin.'
    Assert-Contract ($config.services.'database-migrator'.image -ceq 'neo4flix/database-migrator:0.0.1') 'Migrator must use the versioned Java migrator image.'
    foreach ($name in $expected) {
        $service = $config.services.$name
        Assert-Contract ($service.image -notmatch ':latest(?:$|@)') "$name must not use latest."
    }
    foreach ($name in @('user-service', 'movie-service', 'rating-service', 'recommendation-service')) {
        $service = $config.services.$name
        Assert-Contract ($service.depends_on.'database-migrator'.condition -ceq 'service_completed_successfully') "$name must wait for the migrator to succeed."
        Assert-Contract (-not $service.PSObject.Properties['ports']) "$name must not publish ports in the base topology."
        Assert-Contract ($config.services.web.depends_on.$name.condition -ceq 'service_healthy') "Web must wait for $name health."
        Assert-Contract ($service.environment.NEO4FLIX_JWT_PUBLIC_KEY -ceq 'replace-with-rsa-public-key-or-file-path') "$name must receive the configured JWT public key."
        Assert-Contract ($service.environment.NEO4FLIX_ALLOWED_ORIGINS -ceq 'http://localhost:4200,http://localhost:8080') "$name must receive the configured allowed origins."
    }
    Assert-Contract ($config.services.'user-service'.environment.NEO4FLIX_JWT_PRIVATE_KEY -ceq 'replace-with-pkcs8-rsa-private-key-or-file-path') 'User Service must receive the JWT signing key.'
    Assert-Contract ($config.services.'user-service'.environment.NEO4FLIX_TOTP_ENCRYPTION_KEY -ceq 'replace-with-base64-32-byte-key') 'User Service must receive the TOTP encryption key.'
    foreach ($name in @('movie-service', 'rating-service', 'recommendation-service')) {
        $service = $config.services.$name
        Assert-Contract (-not $service.environment.PSObject.Properties['NEO4FLIX_JWT_PRIVATE_KEY']) "$name must not receive the JWT signing key."
        Assert-Contract (-not $service.environment.PSObject.Properties['NEO4FLIX_TOTP_ENCRYPTION_KEY']) "$name must not receive the TOTP encryption key."
    }
    Assert-Contract ($config.services.'database-migrator'.depends_on.neo4j.condition -ceq 'service_healthy') 'Migrator must wait for Neo4j health.'
    Assert-Contract (-not $config.services.neo4j.PSObject.Properties['ports']) 'Neo4j must not publish ports in the base topology.'
    Assert-Contract ($config.services.neo4j.environment.NEO4J_PLUGINS -ceq '["graph-data-science"]') 'Development GDS plugin configuration is required.'
    Assert-Contract (-not $config.services.neo4j.environment.PSObject.Properties['NEO4J_USERNAME'] -and -not $config.services.neo4j.environment.PSObject.Properties['NEO4J_PASSWORD']) 'Neo4j health credentials must not become unrecognized NEO4J_ configuration settings.'
    Assert-Contract (@($config.services.neo4j.volumes | Where-Object { $_.type -eq 'volume' -and $_.target -eq '/data' }).Count -eq 1) 'Neo4j data must use a named volume.'
    Assert-Contract (@($config.services.'database-migrator'.command).Count -eq 1 -and $config.services.'database-migrator'.command[0] -ceq 'migrate') 'Migrator must run the migrate command.'
    Assert-Contract (-not $config.services.'database-migrator'.PSObject.Properties['volumes']) 'Migrator must run from its built JAR without host script mounts.'
    $migratorDockerfile = [string]$config.services.'database-migrator'.build.dockerfile_inline
    Assert-Contract ($migratorDockerfile -match 'COPY --from=build --chown=10001:10001 /workspace/database/migrator/check-gds\.sh /app/check-gds\.sh') 'Migrator image must include the compatibility wrapper beside its default JAR.'
    Assert-Contract ($migratorDockerfile -match 'RUN chmod 0555 /app/check-gds\.sh') 'Migrator compatibility wrapper must be executable in the final image.'

    $devJson = & docker compose --env-file .env.example -f infra/compose.yml -f infra/compose.dev.yml config --format json
    Assert-Contract ($LASTEXITCODE -eq 0) 'Docker Compose development configuration failed.'
    $dev = ($devJson -join "`n") | ConvertFrom-Json
    $ports = @{ 'user-service' = 8081; 'movie-service' = 8082; 'rating-service' = 8083; 'recommendation-service' = 8084; 'web' = 8080 }
    foreach ($name in $ports.Keys) {
        $bindings = @($dev.services.$name.ports)
        Assert-Contract ($bindings.Count -eq 1 -and $bindings[0].host_ip -eq '127.0.0.1' -and $bindings[0].published -eq [string]$ports[$name] -and $bindings[0].target -eq $ports[$name]) "Unexpected local binding for $name."
    }
    Assert-Contract (@($dev.services.neo4j.ports).Count -eq 2) 'Neo4j must publish Browser and Bolt locally.'
    foreach ($binding in $dev.services.neo4j.ports) {
        Assert-Contract ($binding.host_ip -eq '127.0.0.1' -and $binding.target -in @(7474, 7687) -and $binding.published -eq [string]$binding.target) 'Neo4j development ports must bind localhost only.'
    }
    Write-Host 'Compose configuration contract passed.'
}
finally { Pop-Location }
