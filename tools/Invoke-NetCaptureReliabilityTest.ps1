param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_.:\-]+$')][string]$Serial,
    [ValidateRange(1,30)][int]$Rounds = 10,
    [ValidateRange(30,600)][int]$RoundTimeoutSeconds = 180,
    [string]$OutputDirectory
)

# Serial real-device regression. Open the copied entry in WeChat File Transfer Assistant
# once per round. This runner never sends chat messages or retries failed NET requests.
$ErrorActionPreference = 'Stop'
$package = 'com.otokit.noharayh'
$activity = "$package/.transfer.test.CaptureTestActivity"
if (!$OutputDirectory) { $OutputDirectory = Join-Path $PSScriptRoot ('../app/build/net-reliability/' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$output = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($output) | Out-Null
$reportFile = Join-Path $output 'report.json'
$results = [Collections.Generic.List[object]]::new()
function Invoke-DeviceAdb {
    param([string[]]$Arguments)
    $value = & adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'ADB failed.' }
    return $value
}
function Read-State {
    $value = & adb -s $Serial shell run-as $package cat files/transfer-capture/status.json 2>$null
    if ($LASTEXITCODE -eq 0) { return ($value -join "`n" | ConvertFrom-Json) }
    return $null
}
function Write-Report {
    $report = [ordered]@{ serial=$Serial; game='maimai'; difficulties=@(0,1,2,3,4,10); requestedRounds=$Rounds; rounds=@($results.ToArray()) }
    [IO.File]::WriteAllText($reportFile, ($report | ConvertTo-Json -Depth 16), [Text.UTF8Encoding]::new($false))
}
function Export-Batch([string]$Batch, [string]$Destination) {
    if ($Batch -notmatch '^\d+-[a-z0-9]+$') { throw 'Invalid batch name.' }
    [IO.Directory]::CreateDirectory($Destination) | Out-Null
    $info = [Diagnostics.ProcessStartInfo]::new((Get-Command adb).Source,
        "-s $Serial exec-out run-as $package tar -cf - -C files/transfer-capture $Batch")
    $info.UseShellExecute=$false; $info.RedirectStandardOutput=$true
    $process=[Diagnostics.Process]::Start($info)
    $archive=Join-Path $Destination 'batch.tar'
    $stream=[IO.File]::Create($archive)
    try { $process.StandardOutput.BaseStream.CopyTo($stream) } finally { $stream.Dispose() }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'Batch export failed.' }
    & tar -xf $archive -C $Destination
    if ($LASTEXITCODE -ne 0) { throw 'Batch extraction failed.' }
    return Join-Path $Destination $Batch
}

Write-Output "REPORT $reportFile"
Write-Report
try {
    for ($round=1; $round -le $Rounds; $round++) {
        $started=[DateTimeOffset]::UtcNow
        $clock=[Diagnostics.Stopwatch]::StartNew()
        $result=[ordered]@{ round=$round; launch= $(if($round % 2) {'cold'} else {'warm'}); startedAt=$started.ToString('o'); passed=$false }
        $state=$null; $callbackAt=$null; $returned=$false
        $previousDirectory=(Read-State).directory
        try {
            Invoke-DeviceAdb -Arguments @('shell','am','start','--activity-clear-top','--activity-single-top','-n',$activity,'--ez','stop','true') | Out-Null
            if ($round % 2) { Invoke-DeviceAdb -Arguments @('shell','am','force-stop',$package) | Out-Null }
            Invoke-DeviceAdb -Arguments @('shell','am','start','-f','0x10008000','-W','-n',$activity,'--ez','start','true','--es','game','maimai','--es','difficulties','0,1,2,3,4,10','--ez','openWechat','true') | Out-Null
            $announced=$false
            do {
                Start-Sleep -Milliseconds 750
                $candidate=Read-State
                if (!$candidate -or !$candidate.directory -or $candidate.directory -eq $previousDirectory) { continue }
                $state=$candidate
                if (!$announced -and $state.stage -eq 'WAITING_WECHAT') {
                    Write-Output "ROUND $round/$Rounds $($result.launch) WAITING_WECHAT"
                    $announced=$true
                }
                if (!$returned -and $state.proxyEvents.callback -gt 0) {
                    $callbackAt=$clock.ElapsedMilliseconds; $returned=$true
                    Invoke-DeviceAdb -Arguments @('shell','am','start','--activity-clear-top','--activity-single-top','-n',$activity,'--ez','start','false') | Out-Null
                    Write-Output "ROUND $round CALLBACK_CAPTURED"
                }
                if ($state.stage -in @('SAVED_FOR_INSPECTION','FAILED','TIMEOUT','CANCELLED')) { break }
            } while ($clock.Elapsed.TotalSeconds -lt $RoundTimeoutSeconds)
            $result['stage']=$state.stage
            $result['callbackMs']=$callbackAt
            if ($null -ne $callbackAt) { $result['afterCallbackMs']=$clock.ElapsedMilliseconds-$callbackAt }
            $result['proxyEvents']=$state.proxyEvents
            $result['reason']=$state.reason
            if ($state.directory) {
                $batch=$state.directory.Split('/')[-1]
                $result['batch']=$batch
                $batchPath=Export-Batch $batch (Join-Path $output "round-$round")
                $manifest=Get-Content (Join-Path $batchPath 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
                $pageResults=[Collections.Generic.List[object]]::new()
                foreach ($page in $manifest.pages) {
                    if ($page.file -notmatch '^(player|recent|difficulty_(0|1|2|3|4|10))\.html$') { throw 'Unexpected file.' }
                    $file=Join-Path $batchPath $page.file
                    $body=[IO.File]::ReadAllText($file)
                    $hash=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower()
                    $count=([regex]::Matches($body,'music_name_block')).Count
                    if ($hash -ne $page.sha256 -or !$body.Contains('</html>') -or (Get-Item $file).Length -ne $page.bytes) { throw 'Incomplete or corrupt page.' }
                    if ($page.file.StartsWith('difficulty_') -and $count -le 0) { throw 'Score content missing.' }
                    $pageResults.Add([ordered]@{file=$page.file;bytes=$page.bytes;sha256=$hash;musicBlocks=$count})
                }
                $result['pages']=@($pageResults.ToArray())
            }
            if ($state.stage -ne 'SAVED_FOR_INSPECTION' -or $result.pages.Count -ne 8) { throw 'Round did not complete all eight pages.' }
            $result.passed=$true
        } catch {
            # Controlled local errors only. Never persist URLs, exception details, or HTML.
            $result['failureType']=$_.Exception.GetType().Name
            Write-Output "ROUND $round FAILED stage=$($state.stage)"
        } finally {
            $result['elapsedMs']=$clock.ElapsedMilliseconds
            $results.Add($result); Write-Report
            Invoke-DeviceAdb -Arguments @('shell','am','start','--activity-clear-top','--activity-single-top','-n',$activity,'--ez','stop','true') | Out-Null
        }
        Write-Output "ROUND $round COMPLETE passed=$($result.passed) pages=$($result.pages.Count) elapsedMs=$($result.elapsedMs)"
    }
} finally {
    & adb -s $Serial shell am start --activity-clear-top --activity-single-top -n $activity --ez stop true | Out-Null
    Write-Report
}
Write-Output "FINISHED passed=$(($results | Where-Object passed).Count)/$Rounds report=$reportFile"
