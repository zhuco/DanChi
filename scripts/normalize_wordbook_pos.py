from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


POS_LABELS: dict[str, str] = {
    "n.": "名词",
    "n": "名词",
    "v.": "动词",
    "v": "动词",
    "vt.": "及物动词",
    "vt": "及物动词",
    "vi.": "不及物动词",
    "vi": "不及物动词",
    "adj.": "形容词",
    "adj": "形容词",
    "adv.": "副词",
    "adv": "副词",
    "prep.": "介词",
    "prep": "介词",
    "conj.": "连词",
    "conj": "连词",
    "pron.": "代词",
    "pron": "代词",
    "num.": "数词",
    "num": "数词",
    "art.": "冠词",
    "art": "冠词",
    "int.": "感叹词",
    "int": "感叹词",
    "abbr.": "缩写",
    "abbr": "缩写",
    "aux.": "助动词",
    "aux": "助动词",
    "modal.": "情态动词",
    "modal": "情态动词",
    "phr.": "短语",
    "phr": "短语",
    "det.": "限定词",
    "det": "限定词",
    "pl.": "复数",
    "pl": "复数",
    "sing.": "单数",
    "sing": "单数",
}

POS_ALIASES: dict[str, str] = {
    "noun": "n.",
    "名词": "n.",
    "verb": "v.",
    "动词": "v.",
    "transitiveverb": "vt.",
    "transitive": "vt.",
    "及物动词": "vt.",
    "intransitiveverb": "vi.",
    "intransitive": "vi.",
    "不及物动词": "vi.",
    "adjective": "adj.",
    "形容词": "adj.",
    "adverb": "adv.",
    "副词": "adv.",
    "preposition": "prep.",
    "介词": "prep.",
    "conjunction": "conj.",
    "连词": "conj.",
    "pronoun": "pron.",
    "代词": "pron.",
    "numeral": "num.",
    "number": "num.",
    "数词": "num.",
    "article": "art.",
    "冠词": "art.",
    "interjection": "int.",
    "感叹词": "int.",
    "abbreviation": "abbr.",
    "缩写": "abbr.",
    "auxiliary": "aux.",
    "auxiliaryverb": "aux.",
    "助动词": "aux.",
    "modalverb": "modal.",
    "情态动词": "modal.",
    "phrase": "phr.",
    "短语": "phr.",
    "determiner": "det.",
    "限定词": "det.",
    "plural": "pl.",
    "复数": "pl.",
    "singular": "sing.",
    "单数": "sing.",
}
for key in POS_LABELS:
    POS_ALIASES[key.rstrip(".")] = f"{key.rstrip('.')}."

POS_CODES = [
    "modal",
    "abbr",
    "prep",
    "conj",
    "pron",
    "sing",
    "adj",
    "adv",
    "aux",
    "det",
    "phr",
    "num",
    "art",
    "int",
    "vt",
    "vi",
    "pl",
    "n",
    "v",
]
CHINESE_POS = [
    "不及物动词",
    "及物动词",
    "情态动词",
    "形容词",
    "感叹词",
    "助动词",
    "限定词",
    "名词",
    "动词",
    "副词",
    "介词",
    "连词",
    "代词",
    "数词",
    "冠词",
    "缩写",
    "短语",
    "复数",
    "单数",
]

POS_CODE_PATTERN = "|".join(POS_CODES)
CHINESE_POS_PATTERN = "|".join(CHINESE_POS)
POS_PREFIX_RE = re.compile(rf"^\s*({POS_CODE_PATTERN})(?:\.\s*|[:：]\s*|\s+)(.+)$", re.I)
CHINESE_POS_RE = re.compile(rf"^\s*({CHINESE_POS_PATTERN})(?:[:：]\s*|\s+)(.+)$")
SPLIT_RE = re.compile(
    rf"(?:\r?\n|\\n)|\s*[;；]\s*(?=(?:{POS_CODE_PATTERN})(?:\.|[:：]|\s))|\s*[;；]\s*(?={CHINESE_POS_PATTERN}(?:[:：]|\s))",
    re.I,
)


def normalize_pos(pos: Any) -> str:
    if pos is None:
        return ""
    value = str(pos).strip().lower()
    if not value:
        return ""
    cleaned = (
        value.replace("：", ":")
        .replace("．", ".")
        .replace(" ", "")
        .replace("\t", "")
    )
    cleaned = re.sub(r"。$", ".", cleaned)
    cleaned = re.sub(r"[:：]+$", "", cleaned)
    alias_key = re.sub(r"[\s_-]+", "", cleaned.rstrip("."))
    if alias_key in POS_ALIASES:
        return POS_ALIASES[alias_key]
    if cleaned in POS_LABELS:
        return f"{cleaned.rstrip('.')}."
    return f"{cleaned.rstrip('.')}." if cleaned else ""


def get_pos_name(pos: Any) -> str:
    normalized = normalize_pos(pos)
    return POS_LABELS.get(normalized) or POS_LABELS.get(normalized.rstrip("."), "")


def first_text(source: dict[str, Any], *names: str) -> str:
    for name in names:
        value = source.get(name)
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def infer_pos_from_unit(unit: Any) -> str:
    text = "" if unit is None else str(unit)
    if "不及物动词" in text:
        return "vi."
    if "及物动词" in text:
        return "vt."
    if "动词" in text:
        return "v."
    if "形容词" in text:
        return "adj."
    if "副词" in text:
        return "adv."
    if "名词" in text:
        return "n."
    if "介词" in text:
        return "prep."
    if "连词" in text:
        return "conj."
    if "代词" in text:
        return "pron."
    if "数词" in text:
        return "num."
    if "冠词" in text:
        return "art."
    if "感叹词" in text:
        return "int."
    if "限定词" in text:
        return "det."
    return ""


def parse_meaning_text(text: Any, word_id: str = "word", fallback_pos: str = "") -> list[dict[str, str]]:
    raw = "" if text is None else str(text).strip()
    if not raw:
        return []
    fallback = normalize_pos(fallback_pos)
    parts = [part.strip() for part in SPLIT_RE.split(raw) if part and part.strip()]
    results: list[dict[str, str]] = []
    for part in parts:
        match = POS_PREFIX_RE.match(part)
        chinese_match = CHINESE_POS_RE.match(part) if match is None else None
        if match:
            pos = normalize_pos(match.group(1))
            meaning = match.group(2).strip()
        elif chinese_match:
            pos = normalize_pos(chinese_match.group(1))
            meaning = chinese_match.group(2).strip()
        else:
            pos = fallback
            meaning = part
        if not meaning:
            continue
        results.append(
            {
                "id": f"{word_id}-meaning-{len(results)}",
                "pos": pos,
                "posName": get_pos_name(pos),
                "meaning": meaning,
            }
        )
    return results


def raw_meaning_items(item: dict[str, Any]) -> list[dict[str, str]]:
    values: list[dict[str, str]] = []
    for field in ("meanings", "definitions"):
        array = item.get(field)
        if not isinstance(array, list):
            continue
        for index, value in enumerate(array):
            if isinstance(value, dict):
                values.append(
                    {
                        "id": first_text(value, "id") or f"meaning-{index}",
                        "pos": first_text(value, "pos", "partOfSpeech", "part_of_speech", "wordType", "type"),
                        "posName": first_text(value, "posName", "pos_name"),
                        "meaning": first_text(value, "meaning", "translation", "definition", "cn", "text"),
                    }
                )
            elif str(value).strip():
                values.append(
                    {
                        "id": f"meaning-{index}",
                        "pos": "",
                        "posName": "",
                        "meaning": str(value).strip(),
                    }
                )
    return values


def normalize_word(item: dict[str, Any]) -> tuple[dict[str, Any], bool, bool]:
    word_id = first_text(item, "id", "word") or "word"
    meaning = first_text(item, "meaning", "translation", "definition", "text")
    direct_pos = normalize_pos(first_text(item, "pos", "partOfSpeech", "part_of_speech", "wordType", "type"))
    unit_pos = infer_pos_from_unit(item.get("unit"))
    fallback_pos = direct_pos or unit_pos

    normalized: list[dict[str, str]] = []
    for index, raw_item in enumerate(raw_meaning_items(item)):
        item_pos = normalize_pos(raw_item.get("pos")) or fallback_pos
        parsed = parse_meaning_text(raw_item.get("meaning"), f"{word_id}-meaning-{index}", item_pos)
        for parsed_index, parsed_item in enumerate(parsed):
            pos = normalize_pos(parsed_item.get("pos")) or item_pos
            parsed_item["pos"] = pos
            parsed_item["posName"] = raw_item.get("posName") or parsed_item.get("posName") or get_pos_name(pos)
            if parsed_index == 0 and raw_item.get("id"):
                parsed_item["id"] = raw_item["id"]
            normalized.append(parsed_item)

    if not normalized:
        normalized = parse_meaning_text(meaning, word_id, fallback_pos)

    if not normalized and meaning:
        normalized = [
            {
                "id": f"{word_id}-meaning-0",
                "pos": fallback_pos,
                "posName": get_pos_name(fallback_pos),
                "meaning": meaning,
            }
        ]

    first_pos = next((entry["pos"] for entry in normalized if entry.get("pos")), "")
    had_pos = bool(direct_pos or any(entry.get("pos") for entry in raw_meaning_items(item)))
    filled_pos = not had_pos and bool(first_pos)

    item["meanings"] = normalized
    if first_pos:
        item["pos"] = first_pos
    elif "pos" not in item:
        item["pos"] = ""

    return item, bool(first_pos), filled_pos


def main() -> int:
    parser = argparse.ArgumentParser(description="Normalize wordbook meanings and safe POS fields.")
    parser.add_argument(
        "--wordbook",
        default="app/src/main/assets/wordbooks/zhongkao_words.json",
        help="Path to the wordbook JSON file.",
    )
    parser.add_argument(
        "--report",
        default="docs/missing-pos-report.json",
        help="Path to write missing POS report JSON.",
    )
    args = parser.parse_args()

    wordbook_path = Path(args.wordbook)
    report_path = Path(args.report)
    words = json.loads(wordbook_path.read_text(encoding="utf-8"))
    if not isinstance(words, list):
        raise ValueError(f"{wordbook_path} must contain a JSON array")

    original_ids = [item.get("id") for item in words if isinstance(item, dict)]
    missing: list[dict[str, str]] = []
    filled_count = 0
    with_pos_count = 0
    migrated_count = 0

    for item in words:
        if not isinstance(item, dict):
            continue
        migrated_count += 1
        normalized, has_pos, filled_pos = normalize_word(item)
        if has_pos:
            with_pos_count += 1
        if filled_pos:
            filled_count += 1
        if not has_pos:
            missing.append(
                {
                    "id": first_text(normalized, "id"),
                    "word": first_text(normalized, "word"),
                    "meaning": first_text(normalized, "meaning", "translation", "definition", "text"),
                    "reason": "cannot infer pos from pos/partOfSpeech/wordType/type/meanings/definitions/meaning prefix/unit",
                }
            )

    normalized_ids = [item.get("id") for item in words if isinstance(item, dict)]
    if original_ids != normalized_ids:
        raise RuntimeError("word.id changed during normalization; aborting write")

    wordbook_path.write_text(
        json.dumps(words, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(missing, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(
        json.dumps(
            {
                "wordbook": str(wordbook_path),
                "report": str(report_path),
                "migratedWords": migrated_count,
                "wordsWithPosAfter": with_pos_count,
                "filledPosWords": filled_count,
                "missingPosWords": len(missing),
                "wordIdsChanged": False,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
