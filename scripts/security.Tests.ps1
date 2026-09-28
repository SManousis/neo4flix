$ErrorActionPreference = 'Stop'

Describe 'Security scan wrapper' {
    It 'exposes a make security target' {
        $makefile = Get-Content (Join-Path $PSScriptRoot '..\Makefile') -Raw
        if ($makefile -notmatch '(?m)^security:') { throw 'Makefile does not expose security.' }
        $compose = Get-Content (Join-Path $PSScriptRoot '..\infra\compose.yml') -Raw
        if ($compose -notmatch 'NEO4FLIX_AUTH_TRUSTED_PROXIES') { throw 'Compose does not configure trusted proxy identity.' }
    }

    It 'previews scanner commands without reading or printing secret values' {
        $script = Join-Path $PSScriptRoot 'security.ps1'
        $output = (& pwsh -NoProfile -File $script -WhatIf) -join "`n"
        if ($output -notmatch 'npm audit --prefix frontend --audit-level high') { throw 'npm audit preview missing.' }
        if ($output -notmatch 'dependency-check .*--failOnCVSS 7') { throw 'backend dependency-check threshold preview missing.' }
        if ($output -notmatch 'gitleaks detect') { throw 'gitleaks preview missing.' }
        if ($output -notmatch 'trivy image') { throw 'container image scan preview missing.' }
        if ($output -match 'NEO4FLIX_JWT_PRIVATE_KEY=.*|BEGIN (RSA )?PRIVATE KEY') { throw 'Secret material appeared in preview.' }
    }
}
