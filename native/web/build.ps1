param([switch]$DownloadSdk)
$ErrorActionPreference = 'Stop'
$xingWebRoot = $PSScriptRoot
$xingSdkInfo = Get-Content -LiteralPath (Join-Path $xingWebRoot 'cef-sdk.json') -Raw | ConvertFrom-Json
$xingSdkFolder = Join-Path $xingWebRoot ('vendor/' + $xingSdkInfo.archive.Replace('.tar.bz2', ''))
if (-not (Test-Path -LiteralPath $xingSdkFolder)) {
    if (-not $DownloadSdk) { throw 'CEF SDK missing. Run build.ps1 -DownloadSdk to download the pinned SDK.' }
    $xingVendor = Join-Path $xingWebRoot 'vendor'
    New-Item -ItemType Directory -Path $xingVendor -Force | Out-Null
    $xingArchive = Join-Path $xingVendor $xingSdkInfo.archive
    if (-not (Test-Path -LiteralPath $xingArchive)) {
        Invoke-WebRequest -Uri ($xingSdkInfo.source + $xingSdkInfo.archive) -OutFile $xingArchive -UseBasicParsing
    }
    if ((Get-FileHash -LiteralPath $xingArchive -Algorithm SHA1).Hash.ToLowerInvariant() -ne $xingSdkInfo.sha1) {
        throw 'CEF archive checksum mismatch.'
    }
    & tar -xf $xingArchive -C $xingVendor
    if ($LASTEXITCODE -ne 0) { throw 'CEF extraction failed.' }
}
& cmake -S $xingWebRoot -B (Join-Path $xingWebRoot 'build') -G 'Visual Studio 17 2022' -A x64
if ($LASTEXITCODE -ne 0) { throw 'CEF engine configuration failed.' }
& cmake --build (Join-Path $xingWebRoot 'build') --config Release --target xing-web xing-web-process --parallel 8
if ($LASTEXITCODE -ne 0) { throw 'CEF engine compilation failed.' }
