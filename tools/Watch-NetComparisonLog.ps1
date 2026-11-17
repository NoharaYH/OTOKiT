param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Package,
    [Parameter(Mandatory=$true)][string]$Output
)
$ErrorActionPreference = 'Stop'
$capturePid = (& adb -s $Serial shell pidof $Package | Out-String).Trim()
if ($capturePid -notmatch '^\d+$') { throw 'Start the reference app first.' }
& adb -s $Serial logcat "--pid=$capturePid" -v threadtime -T 1 'Crawler:D' 'HttpCapturerTunnel:D' 'ReferenceCapture:I' '*:S' |
    ForEach-Object {
        $line = $_
        if ($line -match 'Auth request caught') { 'CALLBACK_CAPTURED' }
        elseif ($line -match 'ReferenceCapture.*SAVED diff=\d+ bytes=\d+') { $Matches[0] }
        elseif ($line -match 'Auth url:(https?://\S+)') {
            $auth = [uri]$Matches[1]
            $parameters = @{}
            foreach ($part in $auth.Query.TrimStart('?').Split('&')) {
                $pair = $part.Split('=',2)
                if ($pair.Length -eq 2) { $parameters[$pair[0]] = [uri]::UnescapeDataString($pair[1]) }
            }
            $redirect = [uri]$parameters['redirect_uri']
            'AUTH host={0} path={1} redirect={2}://{3}{4} stateLength={5}' -f $auth.Host,$auth.AbsolutePath,$redirect.Scheme,$redirect.Host,$redirect.AbsolutePath,$parameters['state'].Length
        }
        elseif ($line -match '<title>\s*(404 Not Found|403 Forbidden|500 Internal Server Error)\s*</title>') { 'LOGIN_ERROR_PAGE '+$Matches[1] }
    } | Tee-Object -FilePath $Output
