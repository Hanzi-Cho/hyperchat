<#
.SYNOPSIS
Prints JSON evidence from the manifest of one local APK, without installing it.
.EXAMPLE
.\tools\inspect-legacy-apk.ps1 -Apk .\candidate.apk
.NOTES
Run once per base or split APK. A manifest marker is a static observation;
runtime registration, permission grants, and network use need separate checks.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [string] $Apk,
    [string] $Aapt2
)

$ErrorActionPreference = 'Stop'
$apkPath = (Get-Item -LiteralPath $Apk).FullName
if (-not $Aapt2) {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk) { throw 'Set ANDROID_HOME or supply -Aapt2.' }
    $toolName = if ($env:OS -eq 'Windows_NT') { 'aapt2.exe' } else { 'aapt2' }
    $Aapt2 = Get-ChildItem -Path (Join-Path $sdk "build-tools/*/$toolName") -File |
        Where-Object { $_.Directory.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Directory.Name } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $Aapt2) { throw 'Install stable Android SDK build-tools or supply -Aapt2.' }
}

$badging = & $Aapt2 dump badging $apkPath
if ($LASTEXITCODE -ne 0) { throw "Could not read APK badging: $apkPath" }
$package = $badging | Where-Object { $_ -clike 'package: *' } | Select-Object -First 1
if ($package -notmatch "^package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'") {
    throw "No package/version information in APK badging: $apkPath"
}
$packageName, $versionCode, $versionName = $Matches[1], $Matches[2], $Matches[3]

$manifest = & $Aapt2 dump xmltree --file AndroidManifest.xml $apkPath
if ($LASTEXITCODE -ne 0) { throw "Could not decode AndroidManifest.xml: $apkPath" }
$path = [System.Collections.Generic.List[string]]::new()
$indents = [System.Collections.Generic.List[int]]::new()
$permissions = [System.Collections.Generic.List[string]]::new()
$markers = [System.Collections.Generic.List[object]]::new()
$androidAttribute = '^\s*A: (?:http://schemas\.android\.com/apk/res/android:|android:)(?<attr>name|permission)(?:\([^)]*\))?="(?<value>[^"]+)"'
foreach ($line in $manifest) {
    if ($line -match '^(?<indent>\s*)E: (?<element>[\w.:-]+)') {
        $indent = $Matches.indent.Length
        while ($indents.Count -and $indents[-1] -ge $indent) {
            $indents.RemoveAt($indents.Count - 1)
            $path.RemoveAt($path.Count - 1)
        }
        $indents.Add($indent)
        $path.Add($Matches.element)
        continue
    }
    if ($line -notmatch $androidAttribute) { continue }
    $attribute, $value = $Matches.attr, $Matches.value
    if ($path[-1] -like 'uses-permission*' -and $attribute -eq 'name') {
        $permissions.Add($value)
    }
    if ($value -cmatch '^android\.app\.appfunctions(?:\.|$)|^android\.permission\.BIND_APP_FUNCTION_SERVICE$') {
        $markers.Add([ordered]@{ element = ($path -join '/'); attribute = $attribute; value = $value })
    }
}
if ($path.Count -eq 0) { throw "No manifest elements decoded: $apkPath" }

[ordered]@{
    apk = [IO.Path]::GetFileName($apkPath)
    sha256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    package = $packageName
    versionCode = $versionCode
    versionName = $versionName
    requestedPermissions = @($permissions | Sort-Object -Unique)
    appFunctionsManifestMarkers = $markers.ToArray()
    scope = 'Literal manifest declarations in this APK. Runtime registry, grants and network activity were not tested.'
} | ConvertTo-Json -Depth 4
