<#
.SYNOPSIS
Runs one Hyperchat probe against the original app currently open on an offline Android test device.
.EXAMPLE
.\tools\test-legacy-app.ps1 -Serial emulator-5554 -App vlc
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Serial,
    [Parameter(Mandatory)][ValidateSet('vlc', 'tc', 'aimp', 'anki')][string] $App,
    [switch] $Negative,
    [switch] $FillName,
    [string] $Adb = 'adb'
)

$ErrorActionPreference = 'Stop'
$App = $App.ToLowerInvariant()
if ($Negative -and $App -ne 'vlc') { throw 'The negative control uses the VLC start screen.' }
if ($FillName -and $App -ne 'tc') { throw 'The filename entry test starts in the Total Commander Find files dialog.' }
$packages = @{
    vlc  = @{ id = 'org.videolan.vlc'; code = 13070208 }
    tc   = @{ id = 'com.ghisler.android.TotalCommander'; code = 2490 }
    aimp = @{ id = 'com.aimp.player'; code = 1751 }
    anki = @{ id = 'com.ichi2.anki'; code = 22503300 }
}
function Invoke-Device([string[]] $DeviceArgs) {
    $output = & $Adb -s $Serial @DeviceArgs
    if ($LASTEXITCODE -ne 0) { throw "adb failed on $Serial ($LASTEXITCODE)." }
    $output
}

function Assert-Offline {
    $connectivity = Invoke-Device @('shell', 'dumpsys', 'connectivity')
    if (-not ($connectivity -match '^\s*Active default network:\s*none\s*$')) {
        throw 'An offline test needs a verified absence of a default network. Disconnect this test device and try again.'
    }
}
$target = $packages[$App].id
$version = $packages[$App].code
$installed = Invoke-Device @('shell', 'dumpsys', 'package', $target)
if (-not ($installed -match "^\s*versionCode=$version\b")) { throw "Install the documented $target APK (versionCode=$version) first." }
Assert-Offline
$mode = if ($Negative) { 'legacy-negative' } elseif ($FillName) { 'tc-type' } else { 'legacy' }
$probe = 'dev.hanzicho.hyperchat.fixture.test/dev.hanzicho.hyperchat.fixture.HyperchatProbe'
$lines = Invoke-Device @('shell', 'am', 'instrument', '-w', '-e', 'mode', $mode, '-e', 'app', $App, $probe)
$lines
Assert-Offline
if (-not ($lines -match '^INSTRUMENTATION_RESULT: outcome=PASS\s*$') -or
    -not ($lines -match '^INSTRUMENTATION_CODE: -1\s*$')) {
    throw "Hyperchat $mode/$App did not pass on $Serial. Review the probe output and the original app screen."
}
