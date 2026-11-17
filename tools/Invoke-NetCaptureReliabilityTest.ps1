param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_.:\-]+$')][string]$Serial,
    [ValidateSet('maimai', 'chunithm')][string]$Game = 'maimai',
    [ValidateRange(1,30)][int]$Rounds = 10,
    [ValidateRange(30,600)][int]$RoundTimeoutSeconds = 180,
    [ValidatePattern('^\d+(,\d+)*$')][string]$DifficultySelection,
    [string]$OutputDirectory
)

# Serial real-device regression. Open the copied entry in WeChat File Transfer Assistant
# once per round. This runner never sends chat messages or retries failed NET requests.
$ErrorActionPreference = 'Stop'
$package = 'com.otokit.noharayh'
$activity = "$package/.transfer.test.CaptureTestActivity"
$difficulties = if ($Game -eq 'maimai') { @(0,1,2,3,4,10) } else { @(0,1,2,3,4,5) }
if ($DifficultySelection) {
    $selected = @($DifficultySelection.Split(',') | ForEach-Object { [int]$_ })
    if (@($selected | Select-Object -Unique).Count -ne $selected.Count -or @($selected | Where-Object { $_ -notin $difficulties }).Count) { throw 'Invalid difficulty selection.' }
    $difficulties = $selected
}
$expectedFiles = @('player.html', 'recent.html') + @($difficulties | ForEach-Object { "difficulty_$_.html" })
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
    $report = [ordered]@{ serial=$Serial; game=$Game; difficulties=$difficulties; requestedRounds=$Rounds; rounds=@($results.ToArray()) }
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
            Invoke-DeviceAdb -Arguments @('shell','am','start','-f','0x10008000','-W','-n',$activity,'--ez','start','true','--es','game',$Game,'--es','difficulties',($difficulties -join ','),'--ez','openWechat','true') | Out-Null
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
            $result['netBusinessErrorCode']=$state.netBusinessErrorCode
            $result['captureFailureType']=$state.failureType
            $result['captureFailureClass']=$state.failureClass
            $result['captureFailureLocation']=$state.failureLocation
            $result['protocolIssue']=$state.protocolIssue
            $result['httpTrace']=$state.httpTrace
            $result['httpStatus']=$state.httpStatus
            $result['networkRetries']=$state.networkRetries
            if ($state.directory) {
                $batch=$state.directory.Split('/')[-1]
                $result['batch']=$batch
                $batchPath=Export-Batch $batch (Join-Path $output "round-$round")
                $manifest=Get-Content (Join-Path $batchPath 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
                $pageResults=[Collections.Generic.List[object]]::new()
                foreach ($page in $manifest.pages) {
                    if ($page.file -notin $expectedFiles) { throw 'Unexpected file.' }
                    $file=Join-Path $batchPath $page.file
                    $body=[IO.File]::ReadAllText($file)
                    $hash=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower()
                    $marker = if ($Game -eq 'maimai') { 'music_name_block' } elseif ($page.file -eq 'difficulty_5.html') { 'class="musiclist_worldsend_title"' } else { 'class="music_title"' }
                    $count=([regex]::Matches($body,$marker)).Count
                    if ($hash -ne $page.sha256 -or !$body.Contains('</html>') -or (Get-Item $file).Length -ne $page.bytes) { throw 'Incomplete or corrupt page.' }
                    if ($page.file.StartsWith('difficulty_') -and $count -le 0) { throw 'Score content missing.' }
                    if ($Game -eq 'chunithm') {
                        if ($page.file.StartsWith('difficulty_')) {
                            $index = [int]($page.file -replace '^difficulty_|\.html$', '')
                            $difficultyClass = @('basic','advanced','expert','master','ultima','worldsend')[$index]
                            if (!$body.Contains("musiclist_box bg_$difficultyClass")) { throw 'Unexpected difficulty page.' }
                        } elseif ($page.file -eq 'player.html' -and !$body.Contains('class="player_name"')) { throw 'Player profile missing.' }
                        elseif ($page.file -eq 'recent.html' -and !$body.Contains('playlog')) { throw 'Playlog page missing.' }
                    }
                    # A chart entry may be unplayed. Count actual highscore blocks separately.
                    $played=([regex]::Matches($body,'class="play_musicdata_highscore"')).Count
                    if ($page.PSObject.Properties['scoreCount'] -and $Game -eq 'chunithm' -and $page.file.StartsWith('difficulty_') -and $played -ne $page.scoreCount) { throw 'Numeric score inspection mismatch.' }
                    $pageResults.Add([ordered]@{file=$page.file;sourceFileName=$page.sourceFileName;bytes=$page.bytes;sha256=$hash;musicBlocks=$count;highscoreBlocks=$played;numericScoreCount=$page.scoreCount;inspection=$page.inspection})
                }
                $result['pages']=@($pageResults.ToArray())
                if ($manifest.loginDiagnostic.file -eq 'login_response.html') {
                    $loginFile=Join-Path $batchPath 'login_response.html'
                    $loginBody=[IO.File]::ReadAllText($loginFile)
                    if (!(Get-Item $loginFile).Length.Equals([long]$manifest.loginDiagnostic.bytes) -or !$loginBody.Contains('</html>')) { throw 'Incomplete login diagnostic.' }
                    $result['loginDiagnostic']=[ordered]@{file='login_response.html';bytes=(Get-Item $loginFile).Length;sha256=(Get-FileHash -LiteralPath $loginFile -Algorithm SHA256).Hash.ToLower()}
                }
                if ($manifest.failedResponse.file -eq 'failed_response.html') {
                    $diagnostic=Join-Path $batchPath 'failed_response.html'
                    $hash=(Get-FileHash -LiteralPath $diagnostic -Algorithm SHA256).Hash.ToLower()
                    if ((Get-Item $diagnostic).Length -ne $manifest.failedResponse.bytes -or $hash -ne $manifest.failedResponse.sha256) { throw 'Corrupt failed response diagnostic.' }
                    $result['failedResponse']=[ordered]@{file='failed_response.html';bytes=(Get-Item $diagnostic).Length;sha256=$hash}
                }
            }
            if ($state.stage -ne 'SAVED_FOR_INSPECTION' -or $result.pages.Count -ne $expectedFiles.Count -or
                (Compare-Object $expectedFiles @($result.pages.file))) { throw 'Round did not complete all expected pages.' }
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
