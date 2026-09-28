Describe 'Canonical verification wrapper' {
    It 'includes live auth acceptance in integration mode' {
        $output = (& (Join-Path $PSScriptRoot 'verify.ps1') -Integration -WhatIf) -join "`n"
        if ($output -notmatch 'AuthNeo4jIntegrationIT,AuthProductionContextIT') {
            throw 'Integration verification omitted live auth persistence or HTTP acceptance.'
        }
    }
    It 'previews every acceptance command in order without executing it' {
        $script = Join-Path $PSScriptRoot 'verify.ps1'
        if (-not (Test-Path $script)) { throw 'scripts/verify.ps1 is missing.' }
        $before = (Get-Location).Path
        $output = (& $script -WhatIf) -join "`n"

        if ($output -notmatch 'mvnw(?:\.cmd)? verify[\s\S]*npm ci[\s\S]*npm run lint[\s\S]*npm test -- --run[\s\S]*npm run build[\s\S]*docker compose --env-file \.env\.example -f infra/compose\.yml config') {
            throw "Preview omitted or reordered acceptance commands: $output"
        }
        if ((Get-Location).Path -ne $before) { throw 'Preview changed the caller directory.' }
    }

    It 'previews the test-only path without lint, build, or Compose' {
        $output = (& (Join-Path $PSScriptRoot 'verify.ps1') -TestOnly -WhatIf) -join "`n"
        if ($output -notmatch 'mvnw(?:\.cmd)? test[\s\S]*npm ci[\s\S]*npm test -- --run') {
            throw "Test preview omitted or reordered test commands: $output"
        }
        if ($output -match 'npm run lint|npm run build|docker compose') {
            throw 'Test-only mode included non-test acceptance steps.'
        }
    }

    It 'runs commands in their required directories and stops at the first native failure' {
        $fixture = Join-Path $TestDrive 'checkout with spaces'
        $null = New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts'), (Join-Path $fixture 'frontend'), (Join-Path $fixture 'bin') -Force
        Copy-Item (Join-Path $PSScriptRoot 'verify.ps1') (Join-Path $fixture 'scripts/verify.ps1')
        # Native stand-ins avoid real dependency installs and builds while exercising
        # the wrapper's process exit handling and working-directory contract.
        $windows = $env:OS -eq 'Windows_NT'
        foreach ($command in @('mvnw', 'npm', 'docker')) {
            $folder = if ($command -eq 'mvnw') { $fixture } else { Join-Path $fixture 'bin' }
            if ($windows) {
                $body = "@echo off`r`necho $command %*^|%CD%>>`"%VERIFY_TEST_LOG%`"`r`nif `"%VERIFY_TEST_FAIL%`"==`"$command %*`" exit /b 23`r`nexit /b 0`r`n"
                Set-Content -LiteralPath (Join-Path $folder "$command.cmd") -Value $body -Encoding Ascii
            }
            else {
                $body = '#!/bin/sh' + "`n" + 'printf ''%s|%s\n'' "COMMAND $*" "$PWD" >> "$VERIFY_TEST_LOG"' + "`n" + '[ "$VERIFY_TEST_FAIL" != "COMMAND $*" ] || exit 23' + "`nexit 0`n"
                $path = Join-Path $folder $command
                Set-Content -LiteralPath $path -Value $body.Replace('COMMAND', $command) -Encoding Ascii
                & chmod +x $path
                if ($LASTEXITCODE -ne 0) { throw 'Could not prepare native test command.' }
            }
        }
        $savedPath = $env:PATH
        $savedLog = $env:VERIFY_TEST_LOG
        $savedFail = $env:VERIFY_TEST_FAIL
        $before = (Get-Location).Path
        try {
            $env:PATH = (Join-Path $fixture 'bin') + [IO.Path]::PathSeparator + $env:PATH
            $env:VERIFY_TEST_LOG = Join-Path $fixture 'commands.log'
            $shell = (Get-Process -Id $PID).Path
            $script = Join-Path $fixture 'scripts/verify.ps1'
            $preview = & $shell -NoProfile -ExecutionPolicy Bypass -File $script -WhatIf 2>&1
            if ($LASTEXITCODE -ne 0 -or (Test-Path $env:VERIFY_TEST_LOG)) { throw 'Preview executed a command or failed.' }

            $expected = @('mvnw verify', 'npm ci', 'npm run lint', 'npm test -- --run', 'npm run build', 'docker compose --env-file .env.example -f infra/compose.yml config', 'mvnw -pl backend/platform-common,backend/rating-service,backend/user-service -am -Dtest=Neo4jSchemaIntegrationTest,*ConcurrencyIT,AuthNeo4jIntegrationIT,AuthProductionContextIT -Dsurefire.failIfNoSpecifiedTests=false test')
            for ($failure = 0; $failure -le $expected.Count; $failure++) {
                if (Test-Path $env:VERIFY_TEST_LOG) { Remove-Item -LiteralPath $env:VERIFY_TEST_LOG }
                $env:VERIFY_TEST_FAIL = if ($failure -lt $expected.Count) { $expected[$failure] } else { '' }
                $output = & $shell -NoProfile -ExecutionPolicy Bypass -File $script 2>&1
                $code = $LASTEXITCODE
                if ($failure -lt $expected.Count -and $code -eq 0) { throw "Failure was swallowed at $($expected[$failure]): $output" }
                if ($failure -eq $expected.Count -and $code -ne 0) { throw "Successful commands failed: $output" }
                $actual = @(Get-Content -LiteralPath $env:VERIFY_TEST_LOG)
                $count = [Math]::Min($failure + 1, $expected.Count)
                if ($actual.Count -ne $count) { throw "Expected $count commands before stopping, received $($actual.Count)." }
                for ($i = 0; $i -lt $count; $i++) {
                    $directory = if ($expected[$i].StartsWith('npm ')) { Join-Path $fixture 'frontend' } else { $fixture }
                    if ($actual[$i] -ne "$($expected[$i])|$directory") { throw "Wrong command or directory: $($actual[$i])" }
                }
            }
            if ((Get-Location).Path -ne $before) { throw 'Verification changed the caller directory.' }
        }
        finally {
            $env:PATH = $savedPath
            $env:VERIFY_TEST_LOG = $savedLog
            $env:VERIFY_TEST_FAIL = $savedFail
        }
    }
}
