import argparse
import json
import re
from pathlib import Path

try:
    import pronouncing
except ImportError:  # Optional local enrichment helper.
    pronouncing = None


SUFFIX_HINTS = [
    ("tion", "后缀 -tion 常构成名词"),
    ("sion", "后缀 -sion 常构成名词"),
    ("ment", "后缀 -ment 常表示行为或结果"),
    ("ness", "后缀 -ness 常表示性质或状态"),
    ("ful", "后缀 -ful 常表示“充满……的”"),
    ("less", "后缀 -less 常表示“没有……的”"),
    ("able", "后缀 -able 常表示“能够……的”"),
    ("ible", "后缀 -ible 常表示“能够……的”"),
    ("ly", "后缀 -ly 常用于副词"),
    ("er", "后缀 -er 常表示人、物或比较级"),
    ("or", "后缀 -or 常表示执行动作的人或物"),
    ("ist", "后缀 -ist 常表示从事某类活动的人"),
    ("ity", "后缀 -ity 常表示性质或状态"),
    ("ive", "后缀 -ive 常构成形容词"),
    ("ous", "后缀 -ous 常构成形容词"),
    ("al", "后缀 -al 常构成形容词或名词"),
]

PREFIX_HINTS = [
    ("inter", "前缀 inter- 常表示“相互、在……之间”"),
    ("under", "前缀 under- 常表示“不足、在下方”"),
    ("over", "前缀 over- 常表示“过度、在上方”"),
    ("non", "前缀 non- 常表示否定"),
    ("dis", "前缀 dis- 常表示否定或相反"),
    ("pre", "前缀 pre- 常表示“在……之前”"),
    ("mis", "前缀 mis- 常表示“错误地”"),
    ("un", "前缀 un- 常表示否定"),
    ("re", "前缀 re- 常表示“再次、重新”"),
]

ARPABET_TO_IPA = {
    "AA": "ɑ",
    "AE": "æ",
    "AH": "ʌ",
    "AO": "ɔ",
    "AW": "aʊ",
    "AY": "aɪ",
    "B": "b",
    "CH": "tʃ",
    "D": "d",
    "DH": "ð",
    "EH": "ɛ",
    "ER": "ɝ",
    "EY": "eɪ",
    "F": "f",
    "G": "g",
    "HH": "h",
    "IH": "ɪ",
    "IY": "i",
    "JH": "dʒ",
    "K": "k",
    "L": "l",
    "M": "m",
    "N": "n",
    "NG": "ŋ",
    "OW": "oʊ",
    "OY": "ɔɪ",
    "P": "p",
    "R": "r",
    "S": "s",
    "SH": "ʃ",
    "T": "t",
    "TH": "θ",
    "UH": "ʊ",
    "UW": "u",
    "V": "v",
    "W": "w",
    "Y": "j",
    "Z": "z",
    "ZH": "ʒ",
}


def brief_meaning(meaning: str) -> str:
    value = re.sub(r"\s+", " ", meaning).strip()
    value = re.split(r"[；;]", value, 1)[0]
    return value[:28]


def root_hint(word: str, unit: str) -> str:
    lower = word.lower()
    parts: list[str] = []
    if unit and unit != "综合":
        parts.append(f"构词分类：{unit}")
    for suffix, hint in SUFFIX_HINTS:
        if lower.endswith(suffix) and len(lower) > len(suffix) + 2:
            parts.append(hint)
            break
    for prefix, hint in PREFIX_HINTS:
        if lower.startswith(prefix) and len(lower) > len(prefix) + 2:
            parts.append(hint)
            break
    return "；".join(parts[:2])


def collocations(word: str, unit: str, meaning: str) -> list[str]:
    lower = word.lower()
    if " " in word:
        return [f"learn to {word}", f"try to {word}", f"need to {word}"]
    if any(marker in unit for marker in ("动词", "及物", "不及物")):
        return [f"try to {word}", f"{word} carefully", f"learn to {word}"]
    if "形容词" in unit or "的" in meaning:
        return [f"{word} idea", f"{word} person", f"feel {word}"]
    if "副词" in unit or lower.endswith("ly"):
        return [f"speak {word}", f"work {word}", f"listen {word}"]
    return [f"a {word}", f"the {word}", f"{word} practice"]


def phonetic_for(word: str) -> str:
    if pronouncing is None:
        return ""
    lookup = re.sub(r"[^A-Za-z']", "", word).lower()
    if not lookup:
        return ""
    phones = pronouncing.phones_for_word(lookup)
    if not phones:
        return ""
    return "/" + arpabet_to_ipa(phones[0]) + "/"


def arpabet_to_ipa(phones: str) -> str:
    pieces: list[str] = []
    for raw_phone in phones.split():
        match = re.match(r"([A-Z]+)([012]?)$", raw_phone)
        if not match:
            continue
        base, stress = match.groups()
        if base == "AH" and stress == "0":
            symbol = "ə"
        elif base == "ER" and stress == "0":
            symbol = "ər"
        else:
            symbol = ARPABET_TO_IPA.get(base, "")
        if not symbol:
            continue
        pieces.append(symbol)
    return "".join(pieces)


def enrich_item(item: dict) -> dict:
    word = item.get("word", "").strip()
    meaning = item.get("meaning", "").strip()
    unit = item.get("unit", "").strip()
    return {
        "id": item.get("id", ""),
        "word": word,
        "meaning": meaning,
        "phonetic": phonetic_for(word) or item.get("phonetic", ""),
        "example": item.get("example")
        or f'I wrote the word "{word}" next to its meaning in my notebook.',
        "exampleCn": item.get("exampleCn") or f"我把“{word}”和它的中文释义写在笔记本上。",
        "root": item.get("root") or root_hint(word, unit),
        "synonyms": item.get("synonyms") or [],
        "collocations": item.get("collocations") or collocations(word, unit, meaning),
        "memoryTip": item.get("memoryTip") or f"先听发音，再把 {word} 和「{brief_meaning(meaning)}」连起来读三遍。",
        "book": item.get("book", ""),
        "unit": unit,
        "source": item.get("source", ""),
        "sourceLine": item.get("sourceLine", 0),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "path",
        nargs="?",
        default="app/src/main/assets/wordbooks/zhongkao_words.json",
    )
    args = parser.parse_args()
    path = Path(args.path)
    items = json.loads(path.read_text(encoding="utf-8"))
    enriched = [enrich_item(item) for item in items]
    path.write_text(json.dumps(enriched, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"enriched {len(enriched)} entries in {path}")


if __name__ == "__main__":
    main()
