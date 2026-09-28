[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet('demo', 'audit', 'load')]
    [string]$Seed
)

$ErrorActionPreference = 'Stop'

if (-not $PSCmdlet.ShouldProcess('the configured migrated Neo4j graph', "load $Seed seed data")) {
    return
}

foreach ($name in 'NEO4J_URI', 'NEO4J_USERNAME', 'NEO4J_PASSWORD') {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set; seed commands do not read .env or start containers."
    }
}

& .\mvnw.cmd -pl database/migrator -am package -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw "Could not build database migrator for seed-$Seed."
}

$jar = Get-ChildItem -Path database/migrator/target -Filter 'database-migrator-*.jar' -File |
    Where-Object { $_.Name -notmatch '^original-' -and $_.Name -notmatch '-plain\.jar$' } |
    Select-Object -First 1
if ($null -eq $jar) {
    throw 'Database migrator JAR was not produced.'
}

& java -jar $jar.FullName "seed-$Seed"
if ($LASTEXITCODE -ne 0) {
    throw "Database migrator failed while loading seed-$Seed."
}
