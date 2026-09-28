$repositoryRoot = (Resolve-Path "$PSScriptRoot/..").Path

function Resolve-ValidJdkHome([string]$candidate) {
    if ([string]::IsNullOrWhiteSpace($candidate)) { return }
    try {
        $java = Join-Path $candidate 'bin\java.exe' -ErrorAction Stop
        $javac = Join-Path $candidate 'bin\javac.exe' -ErrorAction Stop
        if (-not (Test-Path -LiteralPath $java -PathType Leaf -ErrorAction Stop) -or
            -not (Test-Path -LiteralPath $javac -PathType Leaf -ErrorAction Stop)) { return }

        # Windows PowerShell represents native stderr (including Java version output)
        # as error records; keep it from becoming a terminating PowerShell error.
        $ErrorActionPreference = 'Continue'
        & $java -version 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { return }
        & $javac -version 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { return }
        (Resolve-Path -LiteralPath $candidate -ErrorAction Stop).Path
    } catch { return }
}

function Find-JdkHome {
    $configured = Resolve-ValidJdkHome $env:JAVA_HOME
    if ($configured) { return $configured }

    foreach ($command in @(Get-Command java.exe,javac.exe -CommandType Application -All -ErrorAction SilentlyContinue)) {
        $candidate = Split-Path (Split-Path $command.Source -Parent) -Parent
        $jdk = Resolve-ValidJdkHome $candidate
        if ($jdk) { return $jdk }

        # PATH entries can be vendor shims or links outside the JDK's bin folder.
        # Ask the executable for its runtime home instead of assuming its layout.
        $settingsOption = '-XshowSettings:properties'
        if ($command.Name -ieq 'javac.exe') { $settingsOption = '-J-XshowSettings:properties' }
        $ErrorActionPreference = 'Continue'
        try {
            $settings = & $command.Source $settingsOption -version 2>&1
            if ($LASTEXITCODE -ne 0) { continue }
            foreach ($line in $settings) {
                if ("$line" -match '^\s*java\.home\s*=\s*(.+?)\s*$') {
                    $jdk = Resolve-ValidJdkHome $Matches[1]
                    if ($jdk) { return $jdk }
                }
            }
        } catch { continue }
    }
    throw 'A runnable JDK (java.exe and javac.exe) is required. Set JAVA_HOME or add its bin directory to PATH.'
}

$javaHome = Find-JdkHome
$previousJavaHome = $env:JAVA_HOME
$previousMavenUserHome = $env:MAVEN_USER_HOME
$previousRepoUrl = $env:MVNW_REPOURL
$temporaryCache = Join-Path (Resolve-Path -LiteralPath $env:TEMP -ErrorAction Stop).Path ('neo4flix-wrapper-mirror-' + [guid]::NewGuid().ToString())
$temporaryCacheCreated = $false

try {
    New-Item -ItemType Directory -Path $temporaryCache -ErrorAction Stop | Out-Null
    $temporaryCacheCreated = $true
    $env:JAVA_HOME = $javaHome
    $env:MAVEN_USER_HOME = $temporaryCache
    $env:MVNW_REPOURL = 'https://repo.maven.apache.org/maven2'
    & "$repositoryRoot/mvnw.cmd" -version
    if ($LASTEXITCODE -ne 0) { throw 'Wrapper failed normal Maven mirror startup.' }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:MAVEN_USER_HOME = $previousMavenUserHome
    $env:MVNW_REPOURL = $previousRepoUrl
    if ($temporaryCacheCreated -and (Test-Path -LiteralPath $temporaryCache)) {
        $resolvedCache = (Resolve-Path -LiteralPath $temporaryCache -ErrorAction Stop).Path
        if ($resolvedCache -ne $temporaryCache) { throw 'Refusing cleanup outside the proof temporary cache.' }
        Remove-Item -LiteralPath $resolvedCache -Recurse -Force -ErrorAction Stop
    }
}
