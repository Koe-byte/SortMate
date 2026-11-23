# Read-only scorer for the five proposal 5A worksheets. All input times are ms.
param([string]$Directory = $PSScriptRoot)
$ErrorActionPreference = 'Stop'

function Samples($name, $header) {
    $path = Join-Path $Directory "$name.csv"
    if ((Get-Content -LiteralPath $path -TotalCount 1) -cne $header) { throw "${name}: unexpected CSV header" }
    $seen = @{}
    foreach ($row in (Import-Csv -LiteralPath $path)) {
        if (!$row.trial -or $seen.ContainsKey($row.trial)) { throw "${name}: blank or repeated trial ID '$($row.trial)'" }
        $seen[$row.trial] = $true
        foreach ($field in $row.PSObject.Properties) {
            if ([string]::IsNullOrWhiteSpace($field.Value)) { throw "$name/$($row.trial): missing $($field.Name)" }
        }
        $row
    }
}

function Number($text) {
    if ("$text" -notmatch '^\d+$') { throw "Expected a nonnegative whole number, got '$text'" }
    [long]$text
}
function Verdict([bool]$passed) { if ($passed) { 'PASS' } else { 'FAIL' } }
function Result($metric, $trial, $status, $measurement) {
    [pscustomobject]@{ Metric = $metric; Trial = $trial; Status = $status; Measurement = $measurement }
}
function Delta($start, $end) {
    $ms = (Number $end) - (Number $start)
    if ($ms -lt 0) { throw 'End precedes start; check clocks and units' }
    $ms
}

$rows = @(Samples 'ocr' 'trial,device,lighting,actual_cents,detected_cents')
$correct = 0
foreach ($r in $rows) {
    $actual = Number $r.actual_cents
    if ($actual -eq 0) { throw "ocr/$($r.trial): invalid printed total" }
    if ($r.detected_cents -ne 'miss' -and (Number $r.detected_cents) -eq $actual) { $correct++ }
}
if ($rows.Count -gt 20) { throw 'Use a new run directory for each set of 20 distinct receipts.' }
$status = if ($rows.Count -eq 20) { Verdict ($correct -ge 18) } else { 'PENDING' }
Result 'OCR' 'set' $status "$correct correct; $($rows.Count)/20 receipts"

$rows = @(Samples 'geofence' 'trial,device,area,crossed_ms,draw_ms')
if (!$rows.Count) { Result 'Geofence' '-' 'PENDING' 'No physical crossings recorded' }
foreach ($r in $rows) {
    if ($r.area -notin 'home','campus') { throw "geofence/$($r.trial): expected home or campus" }
    $start = Number $r.crossed_ms
    if ($r.draw_ms -eq 'timeout') { Result 'Geofence' $r.trial 'FAIL' 'No badge update'; continue }
    $ms = Delta $start $r.draw_ms
    Result 'Geofence' $r.trial (Verdict ($ms -lt 30000)) "$ms ms, $($r.area)"
}

$rows = @(Samples 'realtime' 'trial,network,send_ms,ack_ms,draw_ms,clock_error_ms')
if (!$rows.Count) { Result 'Realtime' '-' 'PENDING' 'No two-device renders recorded' }
foreach ($r in $rows) {
    $send = Number $r.send_ms
    $ack = Number $r.ack_ms
    if ($ack -lt $send) { throw "realtime/$($r.trial): ACK precedes send" }
    if ($r.draw_ms -eq 'timeout') { Result 'Realtime' $r.trial 'FAIL' 'No remote render'; continue }
    $draw = Number $r.draw_ms
    $err = Number $r.clock_error_ms
    # The server write happened between A's send and ACK. B may draw before A receives its ACK.
    $low = [math]::Max(0L, $draw - $ack - $err)
    $high = $draw - $send + $err
    if ($high -lt 0) { throw "realtime/$($r.trial): inconsistent clocks or mismatched bill" }
    $status = if ($high -lt 300) { 'PASS' } elseif ($low -ge 300) { 'FAIL' } else { 'INCONCLUSIVE' }
    Result 'Realtime' $r.trial $status "$low..$high ms, $($r.network)"
}

$rows = @(Samples 'battery' 'trial,device,duration_minutes,start_percent,end_percent')
if (!$rows.Count) { Result 'Battery' '-' 'PENDING' 'No four-hour device run recorded' }
foreach ($r in $rows) {
    $start = Number $r.start_percent
    $end = Number $r.end_percent
    if ($start -gt 100 -or $end -gt $start) { throw "battery/$($r.trial): invalid discharge interval" }
    $loss = $start - $end
    $minutes = Number $r.duration_minutes
    $status = if ($minutes -eq 240) { Verdict ($loss -lt 3) } else { 'INCONCLUSIVE' }
    Result 'Battery' $r.trial $status "$loss percentage points in $minutes min"
}

$rows = @(Samples 'recovery' 'trial,network,queued,confirmed,reconnect_ms,last_ack_ms')
if (!$rows.Count) { Result 'Recovery' '-' 'PENDING' 'No offline batch recorded' }
foreach ($r in $rows) {
    $queued = Number $r.queued
    $confirmed = Number $r.confirmed
    $start = Number $r.reconnect_ms
    if ($queued -ne 5 -or $confirmed -gt $queued) { throw "recovery/$($r.trial): expected a batch of five distinct entries" }
    if ($confirmed -lt 5) { Result 'Recovery' $r.trial 'FAIL' "$confirmed/5 on server"; continue }
    $ms = Delta $start $r.last_ack_ms
    Result 'Recovery' $r.trial (Verdict ($ms -le 5000)) "5/5 on server in $ms ms, $($r.network)"
}
