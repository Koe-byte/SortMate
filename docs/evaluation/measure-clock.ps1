# Measure the offset to ADD to a phone's wall_ms to place it on the host clock.
param([Parameter(Mandatory)][string]$Serial)
$ErrorActionPreference = 'Stop'
$samples = 1..7 | ForEach-Object {
    $start = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $stamp = & adb -s $Serial shell date '+%s%3N'
    $end = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    if ($LASTEXITCODE -ne 0 -or "$stamp".Trim() -notmatch '^\d{13}$') {
        throw "Could not read millisecond wall clock on $Serial."
    }
    [pscustomobject]@{
        Serial = $Serial
        OffsetMs = [long]([math]::Round(($start + $end) / 2) - [long]$stamp)
        ErrorMs = [int]([math]::Ceiling(($end - $start) / 2) + 1)
    }
}
$samples | Sort-Object ErrorMs | Select-Object -First 1
