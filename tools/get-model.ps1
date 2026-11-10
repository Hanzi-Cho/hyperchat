$ErrorActionPreference = 'Stop'

# Fetch only the pinned test model. The app imports a separate copy from its file picker.
$fileName = 'Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm'
$revision = '8768ea43e6d801ee0d353e5036007e6790a2ba05'
$sha256 = 'bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0'
$expectedSize = 343490560
$uri = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/$revision/$fileName"
$artifactDir = Join-Path (Split-Path -Parent $PSScriptRoot) '.artifacts'
$null = New-Item -ItemType Directory -Path $artifactDir -Force
$target = Join-Path $artifactDir $fileName

function Test-ModelIntegrity([string]$Path) {
    (Test-Path -LiteralPath $Path -PathType Leaf) -and
        ((Get-Item -LiteralPath $Path).Length -eq $expectedSize) -and
        ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -eq $sha256)
}

if (Test-ModelIntegrity $target) {
    Write-Output "Verified existing model: $target"
    return
}
if (Test-Path -LiteralPath $target) {
    throw "The existing model failed integrity verification. Move it aside before downloading: $target"
}

$partial = Join-Path $artifactDir ([System.IO.Path]::GetRandomFileName() + '.partial')
try {
    curl.exe --fail --silent --show-error --location --retry 3 --max-time 300 --output $partial $uri
    if ($LASTEXITCODE -ne 0 -or -not (Test-ModelIntegrity $partial)) {
        throw 'Model download failed size or SHA-256 verification.'
    }
    Move-Item -LiteralPath $partial -Destination $target
    Write-Output "Verified model: $target"
} finally {
    if (Test-Path -LiteralPath $partial -PathType Leaf) {
        Remove-Item -LiteralPath $partial
    }
}
