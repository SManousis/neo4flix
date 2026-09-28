[xml]$root = Get-Content -Raw "$PSScriptRoot/../pom.xml"
$rootModules = @('backend', 'database/migrator')
if ((@($root.project.modules.module) -join ',') -ne ($rootModules -join ',')) {
    throw 'Root reactor must contain the backend and one-shot database migrator modules only.'
}

[xml]$backend = Get-Content -Raw "$PSScriptRoot/../backend/pom.xml"
$expected = @('platform-common', 'user-service', 'movie-service', 'rating-service', 'recommendation-service')
if ((@($backend.project.modules.module) -join ',') -ne ($expected -join ',')) {
    throw 'Backend modules differ from the four-service baseline.'
}

if ($backend.project.packaging -ne 'pom') {
    throw 'Backend reactor must use pom packaging.'
}
