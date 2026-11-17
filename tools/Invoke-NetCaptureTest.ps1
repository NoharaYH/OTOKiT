param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_.:\-]+$')][string]$Serial,
    [ValidateSet('maimai', 'chunithm')][string]$Game = 'maimai',
    [ValidatePattern('^\d+(,\d+)*$')][string]$Difficulties = '3',
    [int]$TimeoutSeconds = 600,
    [switch]$UseSystemProxy,
    [switch]$ExportOnly,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$package = 'com.otokit.noharayh'
$activity = "$package/.transfer.test.CaptureTestActivity"
function Invoke-Adb([string[]]$AdbArgs) {
    $result = & adb -s $Serial @AdbArgs
    if ($LASTEXITCODE -ne 0) { throw 'ADB command failed.' }
    return $result
}

$captureError = $null
if ($ExportOnly -and !$OutputDirectory) { throw 'ExportOnly requires OutputDirectory.' }
if (!$ExportOnly) {
# This process must stay alive until finally restores the device settings.
$proxyKeys = @('http_proxy', 'global_http_proxy_host', 'global_http_proxy_port',
    'global_http_proxy_exclusion_list', 'global_proxy_pac_url')
$original = @{}
foreach ($key in $proxyKeys) { $original[$key] = (Invoke-Adb -AdbArgs @('shell','settings','get','global',$key) | Out-String).Trim() }
$proxyApplied = $false
$previousDirectory = $null
$previousRaw = & adb -s $Serial shell run-as $package cat files/transfer-capture/status.json 2>$null
if ($LASTEXITCODE -eq 0) { $previousDirectory = (($previousRaw -join "`n") | ConvertFrom-Json).directory }
try {
    Invoke-Adb -AdbArgs @('shell','am','start','-f','0x10008000','-W','-n',$activity,'--es','game',$Game,'--es','difficulties',$Difficulties,'--ez','start','true','--ez','openWechat','true') | Out-Null
    $readyDeadline = [DateTimeOffset]::UtcNow.AddSeconds(120)
    $state = $null
    do {
        Start-Sleep -Milliseconds 500
        $raw = & adb -s $Serial shell run-as $package cat files/transfer-capture/status.json 2>$null
        if ($LASTEXITCODE -eq 0) {
            $state = ($raw -join "`n") | ConvertFrom-Json
            if (!$state.directory -or $state.directory -eq $previousDirectory) { $state = $null }
            elseif ($state.stage -eq 'FAILED') { throw $state.detail }
        }
        if ([DateTimeOffset]::UtcNow -gt $readyDeadline) { throw 'Capture proxy did not become ready.' }
    } until ($state -and $state.stage -eq 'WAITING_WECHAT')

    $port = [int]$state.proxyPort
    if ($port -lt 1024 -or $port -gt 65535) { throw 'Invalid proxy port.' }
    if ($UseSystemProxy) {
        $proxyApplied = $true
        Invoke-Adb -AdbArgs @('shell','settings','put','global','http_proxy',"127.0.0.1:$port") | Out-Null
    }
    Write-Output "Proxy ready on device port $port. In OTOKiT tap '复制微信入口', then open it in WeChat."
    Write-Output "Output: $($state.directory)"
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $previous = ''
    do {
        $state = ((Invoke-Adb -AdbArgs @('shell','run-as',$package,'cat','files/transfer-capture/status.json')) -join "`n") | ConvertFrom-Json
        $summary = "$($state.stage): $($state.detail)"
        if ($summary -ne $previous) { Write-Output $summary; $previous = $summary }
        if ($state.stage -in @('SAVED_FOR_INSPECTION','FAILED','TIMEOUT','CANCELLED')) { break }
        if ([DateTimeOffset]::UtcNow -gt $deadline) { throw 'Timed out waiting for WeChat / capture.' }
        Start-Sleep -Seconds 2
    } while ($true)
    if ($state.stage -ne 'SAVED_FOR_INSPECTION') { throw $state.detail }
} catch {
    $captureError = $_
} finally {
    # Always restore, including Ctrl+C and a partial/failed experiment.
    if ($proxyApplied) {
        foreach ($key in $proxyKeys) {
            if ($original[$key] -eq 'null' -or $original[$key] -eq '') {
                & adb -s $Serial shell settings delete global $key | Out-Null
            } else {
                & adb -s $Serial shell settings put global $key $original[$key] | Out-Null
            }
            if ($LASTEXITCODE -ne 0) { Write-Warning "Failed to restore $key; reconnect device and restore its proxy." }
        }
        Write-Output 'Original system proxy settings restored.'
    }
    & adb -s $Serial shell am start --activity-clear-top --activity-single-top -n $activity --ez stop true | Out-Null
}
}

if ($OutputDirectory) {
    $target = [System.IO.Path]::GetFullPath($OutputDirectory)
    [System.IO.Directory]::CreateDirectory($target) | Out-Null
    # Avoid PowerShell's text redirection, which corrupts binary tar output on Windows PowerShell.
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = (Get-Command adb).Source
    $info.Arguments = "-s $Serial exec-out run-as $package tar -cf - -C files transfer-capture"
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $process = [System.Diagnostics.Process]::Start($info)
    $archive = Join-Path $target 'transfer-capture.tar'
    $stream = [System.IO.File]::Create($archive)
    try { $process.StandardOutput.BaseStream.CopyTo($stream) } finally { $stream.Dispose() }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'Capture archive export failed.' }
    & tar -xf $archive -C $target
    if ($LASTEXITCODE -ne 0) { throw 'Capture archive extraction failed.' }
    Write-Output "Exported to $target"
}
if ($captureError) { throw $captureError }
