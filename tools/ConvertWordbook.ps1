param(
    [string]$SourcePath = "",
    [string]$OutputPath = "app/src/main/assets/wordbooks/zhongkao_words.json",
    [string]$BookName = "",
    [string]$IdPrefix = "zk",
    [string]$SourceName = ""
)

$ErrorActionPreference = "Stop"

$DefaultBookName = -join ([char]0x521D, [char]0x4E2D, [char]0x4E2D, [char]0x8003)
if ([string]::IsNullOrWhiteSpace($BookName)) {
    $BookName = $DefaultBookName
}
if ([string]::IsNullOrWhiteSpace($SourcePath)) {
    $SourcePath = "$BookName.txt"
}
if ([string]::IsNullOrWhiteSpace($SourceName)) {
    $SourceName = Split-Path -Leaf $SourcePath
}

function Get-StableId([string]$Text, [int]$Index) {
    $normalized = $Text.Trim().ToLowerInvariant() -replace '[^a-z0-9]+', '-'
    $normalized = $normalized.Trim('-')
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        $normalized = "word"
    }
    return "{0}-{1:D4}-{2}" -f $IdPrefix, $Index, $normalized
}

function Get-BriefMeaning([string]$Meaning) {
    $brief = ($Meaning -replace '\s+', ' ').Trim()
    $brief = $brief -replace '[；;].*$', ''
    return $brief
}

function Get-RootHint([string]$Word, [string]$Unit) {
    $parts = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($Unit)) {
        $parts.Add("word formation group: $Unit") | Out-Null
    }

    $lower = $Word.ToLowerInvariant()
    $suffixHints = [ordered]@{
        "tion" = "suffix -tion often forms nouns"
        "sion" = "suffix -sion often forms nouns"
        "ment" = "suffix -ment often means action or result"
        "ness" = "suffix -ness often means quality or state"
        "ful" = "suffix -ful often means full of"
        "less" = "suffix -less often means without"
        "able" = "suffix -able often means able to"
        "ible" = "suffix -ible often means able to"
        "ly" = "suffix -ly is common in adverbs"
        "er" = "suffix -er often marks a person, thing, or comparative"
        "or" = "suffix -or often marks a person or thing"
        "ist" = "suffix -ist often marks a person"
        "ity" = "suffix -ity often means quality or state"
        "ive" = "suffix -ive often forms adjectives"
        "ous" = "suffix -ous often forms adjectives"
        "al" = "suffix -al often forms adjectives or nouns"
    }
    foreach ($suffix in $suffixHints.Keys) {
        if ($lower.EndsWith($suffix) -and $lower.Length -gt ($suffix.Length + 2)) {
            $parts.Add($suffixHints[$suffix]) | Out-Null
            break
        }
    }

    $prefixHints = [ordered]@{
        "un" = "prefix un- often means not"
        "re" = "prefix re- often means again"
        "dis" = "prefix dis- often means not or opposite"
        "pre" = "prefix pre- often means before"
        "mis" = "prefix mis- often means wrongly"
        "non" = "prefix non- often means not"
        "over" = "prefix over- often means too much or above"
        "under" = "prefix under- often means not enough or below"
        "inter" = "prefix inter- often means between"
    }
    foreach ($prefix in $prefixHints.Keys) {
        if ($lower.StartsWith($prefix) -and $lower.Length -gt ($prefix.Length + 2)) {
            $parts.Add($prefixHints[$prefix]) | Out-Null
            break
        }
    }

    return ($parts | Select-Object -First 2) -join "；"
}

function Get-Collocations([string]$Word, [string]$Unit, [string]$Meaning) {
    $isPhrase = $Word.Contains(" ")
    if ($isPhrase) {
        return @("learn to $Word", "try to $Word", "need to $Word")
    }
    if ($Unit -match "动词|及物|不及物") {
        return @("try to $Word", "$Word carefully", "learn to $Word")
    }
    if ($Unit -match "形容词" -or $Meaning -match "的") {
        return @("$Word idea", "$Word person", "feel $Word")
    }
    if ($Unit -match "副词" -or $Word.ToLowerInvariant().EndsWith("ly")) {
        return @("speak $Word", "work $Word", "listen $Word")
    }
    return @("a $Word", "the $Word", "$Word practice")
}

function Get-Example([string]$Word) {
    return "I wrote the word `"$Word`" next to its meaning in my notebook."
}

function Get-ExampleCn([string]$Word) {
    return "Write $Word with its Chinese meaning in your notebook."
}

function Get-MemoryTip([string]$Word, [string]$Meaning) {
    $brief = Get-BriefMeaning $Meaning
    return "Listen first, then read $Word with this meaning three times: $brief."
}

$lines = Get-Content -Encoding UTF8 -Path $SourcePath
$items = New-Object System.Collections.Generic.List[object]
$seen = @{}
$unit = -join ([char]0x7EFC, [char]0x5408)
$index = 1

foreach ($raw in $lines) {
    $line = ($raw -replace '\s+', ' ').Trim()
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }

if ($line -notmatch '^[A-Za-z]') {
        $unit = $line
        continue
    }

    $firstChinese = -1
    for ($i = 0; $i -lt $line.Length; $i++) {
        $code = [int][char]$line[$i]
        if ($code -ge 0x4E00 -and $code -le 0x9FFF) {
            $firstChinese = $i
            break
        }
    }

    if ($firstChinese -lt 1) {
        continue
    }

    $head = $line.Substring(0, $firstChinese).Trim()
    $meaning = $line.Substring($firstChinese).Trim()
    $openParen = [string][char]0xFF08
    if ($head.EndsWith($openParen) -or $head.EndsWith("(")) {
        $pattern = "^(?<word>[A-Za-z][A-Za-z -]*)(?:\s*[\(" + [regex]::Escape($openParen) + "])"
        $match = [regex]::Match($line, $pattern)
        if ($match.Success) {
            $head = $match.Groups["word"].Value.Trim()
            $meaning = $line.Substring($match.Groups["word"].Value.Length).Trim()
        }
    }
    if ($head -notmatch '^[A-Za-z][A-Za-z'' -]*$') {
        $ellipsis = [string][char]0x2026
        if ($head.Contains($ellipsis)) {
            $match = [regex]::Match($line, "^(?<word>[A-Za-z][A-Za-z -]*)")
            if ($match.Success) {
                $head = $match.Groups["word"].Value.Trim()
                $meaning = $line.Substring($match.Groups["word"].Value.Length).Trim()
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($head) -or [string]::IsNullOrWhiteSpace($meaning)) {
        continue
    }

    $word = ($head -split '\s{2,}|,')[0].Trim()
    if ($word -notmatch '^[A-Za-z][A-Za-z'' -]*$') {
        continue
    }

    $key = $word.ToLowerInvariant()
    if ($seen.ContainsKey($key)) {
        $seen[$key].meaning = ($seen[$key].meaning + [string][char]0xFF1B + $meaning)
        continue
    }

    $item = [ordered]@{
        id = Get-StableId $word $index
        word = $word
        meaning = $meaning
        phonetic = ""
        example = Get-Example $word
        exampleCn = Get-ExampleCn $word
        root = Get-RootHint $word $unit
        synonyms = @()
        collocations = Get-Collocations $word $unit $meaning
        memoryTip = Get-MemoryTip $word $meaning
        book = $BookName
        unit = $unit
        source = $SourceName
        sourceLine = $index
    }
    $seen[$key] = $item
    $items.Add([pscustomobject]$item) | Out-Null
    $index++
}

$json = $items | ConvertTo-Json -Depth 5
$directory = Split-Path -Parent $OutputPath
if (-not (Test-Path $directory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}
[System.IO.File]::WriteAllText((Resolve-Path $directory).Path + [System.IO.Path]::DirectorySeparatorChar + (Split-Path -Leaf $OutputPath), $json, [System.Text.UTF8Encoding]::new($false))
Write-Host ("Converted {0} unique entries to {1}" -f $items.Count, $OutputPath)
