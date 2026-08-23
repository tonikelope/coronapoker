function New-CoronaPokerQaSeed {
    [CmdletBinding()]
    [OutputType([long])]
    param()

    # A fresh default explores different schedules across runs. Four bytes are
    # enough for campaign diversity and leave ample room for derived seeds.
    $bytes = New-Object byte[] 4
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    $seed = [long][BitConverter]::ToUInt32($bytes, 0)
    if ($seed -eq 0) {
        return 1L
    }
    return $seed
}
