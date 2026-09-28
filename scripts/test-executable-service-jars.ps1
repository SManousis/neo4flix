$repositoryRoot = (Resolve-Path "$PSScriptRoot/..").Path
$services = @(
    @{ Artifact = 'user-service'; MainClass = 'com.neo4flix.user.UserServiceApplication' },
    @{ Artifact = 'movie-service'; MainClass = 'com.neo4flix.movie.MovieServiceApplication' },
    @{ Artifact = 'rating-service'; MainClass = 'com.neo4flix.rating.RatingServiceApplication' },
    @{ Artifact = 'recommendation-service'; MainClass = 'com.neo4flix.recommendation.RecommendationServiceApplication' }
)
$projects = 'backend/user-service,backend/movie-service,backend/rating-service,backend/recommendation-service'

& "$repositoryRoot/mvnw.cmd" -pl $projects -am package
if ($LASTEXITCODE -ne 0) {
    throw 'Service package build failed.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw 'JAVA_HOME must identify a JDK for executable JAR smoke tests.'
}
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
    throw "Missing Java executable: $java"
}

foreach ($service in $services) {
    $jarPath = Join-Path $repositoryRoot "backend/$($service.Artifact)/target/$($service.Artifact)-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Missing packaged artifact: $jarPath"
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $manifestEntry = $archive.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $manifestEntry) {
            throw "Missing manifest in $jarPath"
        }

        $reader = [System.IO.StreamReader]::new($manifestEntry.Open())
        try {
            $manifest = $reader.ReadToEnd() -replace "`r?`n ", ''
        } finally {
            $reader.Dispose()
        }

        if ($manifest -notmatch 'Main-Class: org\.springframework\.boot\.loader\.launch\.JarLauncher') {
            throw "Missing Spring Boot launcher manifest entry in $jarPath"
        }
        if ($manifest -notmatch "Start-Class: $([regex]::Escape($service.MainClass))") {
            throw "Missing service start class manifest entry in $jarPath"
        }

        $applicationPath = 'BOOT-INF/classes/' + $service.MainClass.Replace('.', '/') + '.class'
        if ($null -eq $archive.GetEntry($applicationPath)) {
            throw "Missing packaged application class $applicationPath in $jarPath"
        }
        if ($null -eq ($archive.Entries | Where-Object { $_.FullName.StartsWith('BOOT-INF/lib/') } | Select-Object -First 1)) {
            throw "Missing packaged dependencies in $jarPath"
        }
    } finally {
        $archive.Dispose()
    }

    $logPath = Join-Path $env:TEMP ("neo4flix-$($service.Artifact)-" + [guid]::NewGuid().ToString() + '.log')
    $process = $null
    try {
        $process = Start-Process -FilePath $java -ArgumentList @(
            '-jar', $jarPath, '--server.port=0', '--management.health.neo4j.enabled=false'
        ) -RedirectStandardOutput $logPath -PassThru
        $deadline = (Get-Date).AddSeconds(30)
        $started = $false
        while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
            if ((Test-Path -LiteralPath $logPath) -and (Get-Content -Raw -LiteralPath $logPath) -match 'Started .*Application') {
                $started = $true
                break
            }
            Start-Sleep -Milliseconds 250
            $process.Refresh()
        }
        if (-not $started) {
            $output = if (Test-Path -LiteralPath $logPath) { Get-Content -Raw -LiteralPath $logPath } else { '' }
            throw "Executable JAR failed startup smoke for $($service.Artifact): $output"
        }
    } finally {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            $process.WaitForExit()
        }
        if (Test-Path -LiteralPath $logPath) {
            Remove-Item -LiteralPath $logPath -Force
        }
    }
}
