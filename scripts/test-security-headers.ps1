$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repository = Split-Path $PSScriptRoot -Parent
$configPath = Join-Path $repository 'infra/nginx/default.conf'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'infra/nginx/default.conf is missing.' }
$config = Get-Content -LiteralPath $configPath -Raw

function Assert-Contains([string] $Needle, [string] $Message) {
    if ($config -notmatch [regex]::Escape($Needle)) { throw $Message }
}

Assert-Contains 'add_header X-Content-Type-Options nosniff always;' 'Nginx must emit nosniff.'
Assert-Contains 'add_header X-Frame-Options DENY always;' 'Nginx must deny framing.'
Assert-Contains 'add_header Referrer-Policy strict-origin-when-cross-origin always;' 'Nginx must set a strict referrer policy.'
Assert-Contains 'add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;' 'Nginx must disable unnecessary browser capabilities.'
Assert-Contains "default-src 'self'" 'Nginx CSP must default to same-origin resources.'
Assert-Contains "script-src 'self'" 'Nginx CSP must restrict scripts to same-origin resources.'
Assert-Contains "style-src 'self' 'unsafe-inline'" 'Nginx CSP must allow Angular component styles without external stylesheets.'
Assert-Contains "img-src 'self' https:" 'Nginx CSP must allow same-origin and HTTPS poster images.'
Assert-Contains "object-src 'none'" 'Nginx CSP must disable plugin objects.'
Assert-Contains "frame-ancestors 'none'" 'Nginx CSP must prevent framing.'
Assert-Contains 'limit_req_zone $binary_remote_addr zone=expensive:' 'Nginx must define a bounded expensive-request limit.'
Assert-Contains 'limit_req zone=expensive' 'Nginx must apply a bounded expensive-request limit.'
Assert-Contains 'limit_req_status 429;' 'Nginx rate-limit violations must return 429.'
if ($config -match 'Access-Control-Allow-Origin\s+\*') { throw 'Nginx must not enable wildcard credentialed CORS.' }

Write-Output 'Nginx security header contract passed.'
