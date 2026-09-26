param(
 [Parameter(Mandatory=$true)][string]$Apk,
 [string]$PreviousApk = '',
 [string]$Output = ''
)
$ErrorActionPreference = 'Stop'
$Apk = (Resolve-Path -LiteralPath $Apk).Path
# Windows SDK native tools can reject non-ASCII paths; verify a byte-identical
# temporary copy, while leaving the user's APK untouched.
$checkPath = Join-Path $env:TEMP ('cookx-apk-check-' + [guid]::NewGuid().ToString('N') + '.apk')
Copy-Item -LiteralPath $Apk -Destination $checkPath
$Apk = $checkPath
try {
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools/35.0.0'
$badging = (& (Join-Path $buildTools 'aapt.exe') dump badging $Apk) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'APK resources cannot be read' }
if ($badging -notmatch "package: name='com.smartcooking.app.localtest' versionCode='(\d+)' versionName='([^']+)'") { throw 'Wrong APK identity' }
$versionCode = [int]$Matches[1]; $versionName = $Matches[2]
if ($badging -notmatch "launchable-activity: name='com.smartcooking.app.MainActivity'") { throw 'Missing launcher activity' }
if ($badging -notmatch "application: label='CookX 本地测试' icon='([^']+)'") { throw 'Missing test edition label/icon' }
$icon = $Matches[1]
function Read-Signer([string]$Path) {
 $result = (& (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $Path) -join "`n"
 if ($LASTEXITCODE -ne 0 -or $result -notmatch 'certificate SHA-256 digest: ([a-f0-9]+)') { throw 'APK signature verification failed' }
 return $Matches[1]
}
$signer = Read-Signer $Apk
if ($PreviousApk -and (Read-Signer (Resolve-Path -LiteralPath $PreviousApk).Path) -ne $signer) { throw 'Signing certificate differs from previous local APK' }
& (Join-Path $buildTools 'zipalign.exe') -c -P 16 4 $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment check failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($Apk)
try {
 function Read-Entry([string]$Name) {
  $entry = $zip.GetEntry($Name)
  if (!$entry) { throw "Missing packaged file: $Name" }
  $reader = [IO.StreamReader]::new($entry.Open())
  try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
 }
 $marker = Read-Entry 'assets/public/local-test-build.json' | ConvertFrom-Json
 $config = Read-Entry 'assets/capacitor.config.json' | ConvertFrom-Json
 if ($marker.edition -ne 'local-test' -or $config.appId -ne 'com.smartcooking.app.localtest' -or $config.server.url) { throw 'Wrong bundled edition or remote server URL' }
 if (!$zip.GetEntry($icon)) { throw 'Launcher icon resource missing' }
 $hasActivity = $false
 foreach ($entry in $zip.Entries | Where-Object FullName -Match '^classes\d*\.dex$') {
  if ((Read-Entry $entry.FullName).Contains('Lcom/smartcooking/app/MainActivity;')) { $hasActivity = $true }
 }
 if (!$hasActivity) { throw 'Launcher class absent from DEX' }
} finally { $zip.Dispose() }
$record = [ordered]@{
 applicationId='com.smartcooking.app.localtest'; label='CookX 本地测试'; versionCode=$versionCode; versionName=$versionName
 launcher='com.smartcooking.app.MainActivity'; launcherIcon=$icon; bundledLocalTest=$true
 signatureVerified=$true; signerSHA256=$signer; comparedPreviousApk=[bool]$PreviousApk; alignmentVerified=$true
 apkSHA256=(Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash; bytes=(Get-Item -LiteralPath $Apk).Length
 deviceInstallation='not verified: no connected Android device'
}
$json = $record | ConvertTo-Json
if ($Output) { [IO.File]::WriteAllText([IO.Path]::GetFullPath($Output),$json,[Text.UTF8Encoding]::new($false)) }
$json

} finally { Remove-Item -LiteralPath $checkPath -ErrorAction SilentlyContinue }
