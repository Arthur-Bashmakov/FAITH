$ErrorActionPreference = 'Stop'

$forbidden = @(
    'C:\\Users\\',
    'AppData',
    '(?i)(api[_-]?key|access[_-]?token)\s*[:=]\s*["'']?[A-Za-z0-9_\-]{20,}',
    'BEGIN PRIVATE KEY',
    'ghp_[A-Za-z0-9]{20,}'
)

$localArtifactPrefixes = @(
    'docs-work/',
    'presentation-work/',
    'release/'
)
$trackedCandidates = git ls-files --cached --others --exclude-standard | Where-Object {
    $candidate = $_
    -not ($localArtifactPrefixes | Where-Object { $candidate.StartsWith($_) })
}
$failed = $false
foreach ($pattern in $forbidden) {
    $matches = $trackedCandidates | Select-String -Pattern $pattern -ErrorAction SilentlyContinue
    if ($matches) {
        Write-Host "Forbidden pattern in file name: $pattern" -ForegroundColor Red
        $failed = $true
    }
    foreach ($file in $trackedCandidates | Where-Object {
        $_ -ne 'scripts/check_repository.ps1' -and
        $_ -match '\.(kt|kts|py|md|ya?ml|xml|properties|pro|ps1|txt|example)$'
    }) {
        if (Test-Path -LiteralPath $file -PathType Leaf) {
            $contentMatch = Select-String -LiteralPath $file -Pattern $pattern -ErrorAction SilentlyContinue
            if ($contentMatch) {
                Write-Host "Check ${file}: found pattern $pattern" -ForegroundColor Red
                $failed = $true
            }
        }
    }
}

if ($failed) { exit 1 }
Write-Host 'Repository check passed.' -ForegroundColor Green
