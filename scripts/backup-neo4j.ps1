[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Destination,

    [string]$ContainerName = 'neo4flix-neo4j-1'
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is required.'
}

$destinationPath = [System.IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $destinationPath -Force | Out-Null

$inspect = & docker inspect --format '{{.State.Running}}|{{.Config.Image}}' $ContainerName 2>$null
if ($LASTEXITCODE -ne 0) { throw "Container '$ContainerName' was not found." }
$inspectParts = $inspect.Trim().Split('|', 2)
$wasRunning = $inspectParts[0] -eq 'true'
$image = $inspectParts[1]
if ([string]::IsNullOrWhiteSpace($image)) { throw "Could not resolve the image for '$ContainerName'." }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dumpName = "neo4j-$stamp.dump"
$rawDump = Join-Path $destinationPath 'neo4j.dump'
$target = Join-Path $destinationPath $dumpName
$stopped = $false
try {
    if ($wasRunning) {
        & docker stop $ContainerName | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not stop container '$ContainerName'." }
        $stopped = $true
    }
    if (Test-Path -LiteralPath $rawDump) { Remove-Item -LiteralPath $rawDump -Force }
    & docker run --rm --volumes-from $ContainerName -v "${destinationPath}:/backup" --user neo4j --entrypoint neo4j-admin $image database dump neo4j --to-path=/backup --overwrite-destination | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'neo4j-admin database dump failed.' }
    if (-not (Test-Path -LiteralPath $rawDump -PathType Leaf)) { throw 'neo4j-admin did not create a dump file.' }
    Move-Item -LiteralPath $rawDump -Destination $target -Force
}
finally {
    if ($stopped) { & docker start $ContainerName | Out-Null }
}

Write-Output "Neo4j dump written to $target"
