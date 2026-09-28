$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-Contract([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Get-ChildText([System.Xml.XmlNode] $Node, [string] $Name) {
    $child = @($Node.ChildNodes | Where-Object { $_.Name -eq $Name } | Select-Object -First 1)
    if ($child.Count -eq 0) { return '' }
    return [string] $child[0].InnerText
}

$repositoryRoot = (Resolve-Path "$PSScriptRoot/..").Path
$services = @('user-service', 'rating-service')

foreach ($service in $services) {
    $pomPath = Join-Path $repositoryRoot "backend/$service/pom.xml"
    [xml] $pom = Get-Content -Raw -LiteralPath $pomPath

    $driverDependencies = @($pom.project.dependencies.dependency | Where-Object {
        $_.groupId -eq 'org.neo4j.driver' -and $_.artifactId -eq 'neo4j-java-driver'
    })
    Assert-Contract ($driverDependencies.Count -eq 1) "$service must declare exactly one explicit Neo4j Java driver dependency."
    Assert-Contract ($driverDependencies[0].version -ceq '6.2.0') "$service must pin Neo4j Java Driver to 6.2.0."
    $driverScope = Get-ChildText $driverDependencies[0] 'scope'
    Assert-Contract ([string]::IsNullOrWhiteSpace($driverScope) -or $driverScope -in @('compile', 'runtime')) "$service must resolve Neo4j Java Driver at compile/runtime scope."

    $testJarDependencies = @($pom.project.dependencies.dependency | Where-Object {
        $classifier = Get-ChildText $_ 'classifier'
        $_.groupId -eq 'com.neo4flix' -and $_.artifactId -eq 'platform-common' -and
            $classifier -eq 'tests'
    })
    Assert-Contract ($testJarDependencies.Count -eq 1 -and (Get-ChildText $testJarDependencies[0] 'scope') -eq 'test') "$service must retain the platform-common test JAR dependency."

    $testcontainersDependencies = @($pom.project.dependencies.dependency | Where-Object {
        $_.groupId -eq 'org.testcontainers' -and $_.artifactId -eq 'neo4j'
    })
    Assert-Contract ($testcontainersDependencies.Count -eq 1 -and (Get-ChildText $testcontainersDependencies[0] 'scope') -eq 'test') "$service must retain the Neo4j Testcontainers dependency."
}

& "$repositoryRoot/mvnw.cmd" -pl 'backend/user-service,backend/rating-service' -am -DskipTests package
if ($LASTEXITCODE -ne 0) {
    throw 'Service package build failed.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($service in $services) {
    $jarPath = Join-Path $repositoryRoot "backend/$service/target/$service-0.0.1-SNAPSHOT.jar"
    Assert-Contract (Test-Path -LiteralPath $jarPath -PathType Leaf) "Missing packaged artifact: $jarPath"
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $driverEntry = $archive.GetEntry('BOOT-INF/lib/neo4j-java-driver-6.2.0.jar')
        Assert-Contract ($null -ne $driverEntry) "$service runtime JAR must package Neo4j Java Driver 6.2.0."
    }
    finally {
        $archive.Dispose()
    }
}

Write-Host 'Runtime driver dependency contract passed: user-service and rating-service package Neo4j Java Driver 6.2.0.'
