[CmdletBinding()]
param(
    [string]$ApkDirectory,
    [string]$AdbPath,
    [string]$Serial
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if ([string]::IsNullOrWhiteSpace($ApkDirectory)) {
    $ApkDirectory = Join-Path $PSScriptRoot '../app/build/outputs/apk/performance'
}

function Read-ZipEntry {
    param([System.IO.Compression.ZipArchive]$Archive, [string]$Name)
    $entry = $Archive.GetEntry($Name)
    if ($null -eq $entry) { throw "Missing $Name in $($Archive.ToString()). Rebuild with :app:assemblePerformance." }
    $inputStream = $entry.Open()
    $outputStream = [System.IO.MemoryStream]::new()
    try {
        $inputStream.CopyTo($outputStream)
        return ,$outputStream.ToArray()
    } finally {
        $inputStream.Dispose()
        $outputStream.Dispose()
    }
}

function Read-ProfileDexRecords {
    param([byte[]]$Data)
    if ($Data.Length -lt 17 -or [System.Text.Encoding]::ASCII.GetString($Data, 0, 4) -ne "pro`0") {
        throw 'The compilation profile has an invalid header. Rebuild the optimized APK and its metadata together.'
    }
    $version = [System.Text.Encoding]::ASCII.GetString($Data, 4, 3)
    $records = @()
    if ($version -eq '010') {
        $count = [int]$Data[8]
        $compressedSize = [System.BitConverter]::ToUInt32($Data, 13)
        # ART version 010 uses zlib. DeflateStream consumes the payload between its header and checksum.
        $compressed = [System.IO.MemoryStream]::new($Data, 19, $compressedSize - 6)
        $inflater = [System.IO.Compression.DeflateStream]::new($compressed, [System.IO.Compression.CompressionMode]::Decompress)
        $inflated = [System.IO.MemoryStream]::new()
        try {
            $inflater.CopyTo($inflated)
            $payload = $inflated.ToArray()
        } finally {
            $inflater.Dispose()
            $compressed.Dispose()
            $inflated.Dispose()
        }
        $offset = 0
        for ($index = 0; $index -lt $count; $index++) {
            $keySize = [System.BitConverter]::ToUInt16($payload, $offset)
            $checksum = [System.BitConverter]::ToUInt32($payload, $offset + 8)
            $key = [System.Text.Encoding]::UTF8.GetString($payload, $offset + 16, $keySize)
            $records += "$key=$checksum"
            $offset += 16 + $keySize
        }
    } elseif ($version -eq '015') {
        $sectionCount = [System.BitConverter]::ToUInt32($Data, 8)
        $offset = $null
        for ($index = 0; $index -lt $sectionCount; $index++) {
            $section = 12 + $index * 16
            if ([System.BitConverter]::ToUInt32($Data, $section) -eq 0) {
                $offset = [System.BitConverter]::ToUInt32($Data, $section + 4)
                break
            }
        }
        if ($null -eq $offset) { throw 'The compilation profile has no DEX section.' }
        $count = [System.BitConverter]::ToUInt16($Data, $offset)
        $offset += 2
        for ($index = 0; $index -lt $count; $index++) {
            $checksum = [System.BitConverter]::ToUInt32($Data, $offset)
            $keySize = [System.BitConverter]::ToUInt16($Data, $offset + 12)
            $key = [System.Text.Encoding]::UTF8.GetString($Data, $offset + 14, $keySize)
            $records += "$key=$checksum"
            $offset += 14 + $keySize
        }
    } else {
        throw "Unsupported compilation profile version $version. Use an installer matching this build."
    }
    return $records | Sort-Object
}

if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $adbCommand) { $AdbPath = $adbCommand.Source }
    $sdkDirectories = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android/Sdk'))
    $propertiesPath = Join-Path $PSScriptRoot '../local.properties'
    if (Test-Path -LiteralPath $propertiesPath) {
        $sdkLine = Get-Content -LiteralPath $propertiesPath | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($null -ne $sdkLine) {
            $sdkDirectories += $sdkLine.Substring(8).Replace('\\', '\').Replace('\:', ':').Replace('\ ', ' ')
        }
    }
    foreach ($sdkDirectory in $sdkDirectories) {
        if ($AdbPath -or [string]::IsNullOrWhiteSpace($sdkDirectory)) { continue }
        $candidate = Join-Path $sdkDirectory 'platform-tools/adb.exe'
        if (Test-Path -LiteralPath $candidate) { $AdbPath = $candidate }
    }
}
if (-not $AdbPath) { throw 'ADB was not found. Add Android platform-tools to PATH or supply -AdbPath.' }

function Invoke-Adb {
    param([string[]]$Arguments)
    $deviceArguments = @()
    if ($Serial) { $deviceArguments = @('-s', $Serial) }
    # ADB also uses stderr for normal daemon startup messages on Windows PowerShell.
    $ErrorActionPreference = 'Continue'
    $output = & $AdbPath @deviceArguments @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($exitCode -ne 0) { throw "ADB failed ($exitCode). $($output -join [Environment]::NewLine)" }
    return $output -join [Environment]::NewLine
}

$ApkDirectory = (Resolve-Path -LiteralPath $ApkDirectory).Path
$metadata = Get-Content -LiteralPath (Join-Path $ApkDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.variantName -ne 'performance' -or $metadata.applicationId -ne 'com.example.auraplay' -or $metadata.elements.Count -ne 1) {
    throw 'Choose the complete output directory from :app:assemblePerformance.'
}
$apkPath = Join-Path $ApkDirectory $metadata.elements[0].outputFile
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) { throw "The optimized APK is missing from $ApkDirectory." }

if (-not $Serial) {
    $connected = @((Invoke-Adb -Arguments @('devices')) -split '\r?\n' | Where-Object { $_ -match '^\S+\s+device\s*$' })
    if ($connected.Count -ne 1) { throw 'Connect one authorized Android device, or choose a device with -Serial.' }
    $Serial = ($connected[0] -split '\s+')[0]
}
$apiLevel = [int](Invoke-Adb -Arguments @('shell', 'getprop', 'ro.build.version.sdk')).Trim()
$installArguments = @('install', '-r', $apkPath)
if ($apiLevel -ge 28) {
    $profile = $metadata.baselineProfiles | Where-Object { $apiLevel -ge $_.minApi -and $apiLevel -le $_.maxApi } | Select-Object -First 1
    if ($null -eq $profile -or $profile.baselineProfiles.Count -ne 1) {
        throw "No install-time compilation profile is available for Android API $apiLevel. Rebuild the complete optimized output."
    }
    $dmPath = Join-Path $ApkDirectory $profile.baselineProfiles[0]
    if ([System.IO.Path]::GetFileNameWithoutExtension($apkPath) -ne [System.IO.Path]::GetFileNameWithoutExtension($dmPath)) {
        throw 'The APK and its compilation metadata must have matching file names.'
    }
    $apk = $null
    $dm = $null
    try {
        $apk = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
        $dm = [System.IO.Compression.ZipFile]::OpenRead($dmPath)
        $apkRecords = @(Read-ProfileDexRecords -Data (Read-ZipEntry -Archive $apk -Name 'assets/dexopt/baseline.prof'))
        $dmRecords = @(Read-ProfileDexRecords -Data (Read-ZipEntry -Archive $dm -Name 'primary.prof'))
        if (-not $apkRecords.Count -or ($apkRecords -join ';') -ne ($dmRecords -join ';')) {
            throw 'The compilation metadata belongs to a different APK. Rebuild or download the complete optimized bundle.'
        }
    } finally {
        if ($null -ne $apk) { $apk.Dispose() }
        if ($null -ne $dm) { $dm.Dispose() }
    }
    $installArguments = @('install-multiple', '-r', $apkPath, $dmPath)
}

Write-Host "Installing optimized AuraPlay on $Serial (Android API $apiLevel)..."
Write-Host (Invoke-Adb -Arguments $installArguments)
$packageDetails = Invoke-Adb -Arguments @('shell', 'dumpsys', 'package', $metadata.applicationId)
$compilation = @($packageDetails -split '\r?\n' | Where-Object { $_ -match '\[status=' })
if ($apiLevel -ge 28 -and -not ($compilation -match 'status=speed-profile|status=speed\]')) {
    Write-Warning 'AuraPlay installed, but Android has not reported a compiled profile. Check device dexopt state before measuring first-launch performance.'
}
Write-Host 'Compilation state'
$compilation | ForEach-Object { Write-Host $_.Trim() }
Write-Host 'Installation complete. Open AuraPlay to check scrolling.'
