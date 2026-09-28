[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$DumpFile,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$ContainerName,

    [switch]$ConfirmRestore,
    [switch]$AllowProjectContainer
)

$ErrorActionPreference = 'Stop'

if (-not $ConfirmRestore) {
    throw 'Refusing restore: pass -ConfirmRestore explicitly after verifying the target is disposable.'
}
if (-not $AllowProjectContainer -and $ContainerName -match 'neo4flix[-_]neo4j') {
    throw "Refusing restore into project container '$ContainerName'. Use a disposable target or explicitly pass -AllowProjectContainer."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is required.'
}

$dumpPath = [System.IO.Path]::GetFullPath($DumpFile)
if (-not (Test-Path -LiteralPath $dumpPath -PathType Leaf)) {
    throw "Dump file does not exist: $dumpPath"
}

$inspect = & docker inspect --format '{{.State.Running}}|{{.Config.Image}}' $ContainerName 2>$null
if ($LASTEXITCODE -ne 0) { throw "Container '$ContainerName' was not found." }
$inspectParts = $inspect.Trim().Split('|', 2)
$wasRunning = $inspectParts[0] -eq 'true'
$image = $inspectParts[1]
if ([string]::IsNullOrWhiteSpace($image)) { throw "Could not resolve the image for '$ContainerName'." }

$stopped = $false
$stagingPath = Join-Path (Split-Path -Parent $dumpPath) ('.neo4j-restore-' + [guid]::NewGuid().ToString('N'))
try {
    if ($wasRunning) {
        & docker stop $ContainerName | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not stop container '$ContainerName'." }
        $stopped = $true
    }
    New-Item -ItemType Directory -Path $stagingPath -Force | Out-Null
    Copy-Item -LiteralPath $dumpPath -Destination (Join-Path $stagingPath 'neo4j.dump')
    & docker run --rm --volumes-from $ContainerName -v "${stagingPath}:/restore:ro" --user neo4j --entrypoint neo4j-admin $image database load neo4j --from-path=/restore --overwrite-destination | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'neo4j-admin database load failed.' }
    Write-Output "Neo4j dump restored into explicitly named container '$ContainerName'."
}
finally {
    if ($stopped) {
        & docker start $ContainerName | Out-Null
    }
    if (Test-Path -LiteralPath $stagingPath) {
        Remove-Item -LiteralPath $stagingPath -Recurse -Force
    }
}
