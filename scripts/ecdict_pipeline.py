from __future__ import annotations

import argparse
import csv
import json
import re
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


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
}
for key in POS_LABELS:
    POS_ALIASES[key.rstrip(".")] = f"{key.rstrip('.')}."

POS_CODES = [
    "modal",
    "abbr",
    "prep",
    "conj",
    "pron",
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
]

POS_CODE_PATTERN = "|".join(POS_CODES)
CHINESE_POS_PATTERN = "|".join(CHINESE_POS)
POS_PREFIX_RE = re.compile(rf"^\s*({POS_CODE_PATTERN})(?:\.\s*|[:：]\s*|\s+)(.+)$", re.I)
CHINESE_POS_RE = re.compile(rf"^\s*({CHINESE_POS_PATTERN})(?:[:：]\s*|\s+)(.+)$")
SPLIT_RE = re.compile(
    rf"(?:\r?\n|\\n)|\s*[;；]\s*(?=(?:{POS_CODE_PATTERN})(?:\.|[:：]|\s))|\s*[;；]\s*(?={CHINESE_POS_PATTERN}(?:[:：]|\s))",
    re.I,
)
CJK_RE = re.compile(r"[\u4e00-\u9fff]")
MOJIBAKE_RE = re.compile(r"(?:�|Ã|Â|锛|鑻|涓|绋|藉|瑟|鈥|â)")
SQLITE_SUFFIXES = {".db", ".sqlite", ".sqlite3"}


@dataclass(frozen=True)
class ParsedMeaning:
    id: str
    pos: str
    pos_name: str
    meaning: str
    explicit_pos: bool

    def to_json(self, example: str = "", translation: str = "") -> dict[str, str]:
        data = {
            "id": self.id,
            "pos": self.pos,
            "posName": self.pos_name,
            "meaning": self.meaning,
        }
        if example:
            data["example"] = example
        if translation:
            data["translation"] = translation
        return data


@dataclass
class TargetEntry:
    word: str
    id: str = ""
    level: str = ""
    tags: list[str] | None = None
    metadata: dict[str, Any] | None = None

    @property
    def key(self) -> str:
        return normalize_word_key(self.word)


@dataclass
class WordbookWordSpec:
    word: str
    stable_word_id: str = ""
    unit: str = ""
    level: str = ""
    tags: list[str] | None = None
    priority: int = 0
    sort_order: int = 0
    source: str = ""

    @property
    def key(self) -> str:
        return normalize_word_key(self.word)


@dataclass
class WordbookSpec:
    id: str
    title: str
    description: str = ""
    stage: str = ""
    version: str = "1.0.0"
    source: str = ""
    sort_order: int = 0
    words: list[WordbookWordSpec] | None = None


def normalize_word_key(word: Any) -> str:
    return str(word or "").strip().lower()


def normalize_pos(pos: Any) -> str:
    if pos is None:
        return ""
    value = str(pos).strip().lower()
    if not value:
        return ""
    cleaned = (
        value.replace("：", ":")
        .replace("．", ".")
        .replace("\t", "")
        .replace(" ", "")
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


def parse_meaning_text(text: Any, word_id: str = "word", fallback_pos: str = "") -> list[dict[str, str]]:
    return [
        parsed.to_json()
        for parsed in parse_meaning_items(text=text, word_id=word_id, fallback_pos=fallback_pos)
    ]


def parse_meaning_items(text: Any, word_id: str = "word", fallback_pos: str = "") -> list[ParsedMeaning]:
    raw = "" if text is None else str(text).strip()
    if not raw:
        return []
    fallback = normalize_pos(fallback_pos)
    parts = [part.strip() for part in SPLIT_RE.split(raw) if part and part.strip()]
    results: list[ParsedMeaning] = []
    for part in parts:
        match = POS_PREFIX_RE.match(part)
        chinese_match = CHINESE_POS_RE.match(part) if match is None else None
        explicit_pos = match is not None or chinese_match is not None
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
            ParsedMeaning(
                id=f"{word_id}-meaning-{len(results)}",
                pos=pos,
                pos_name=get_pos_name(pos),
                meaning=meaning,
                explicit_pos=explicit_pos,
            )
        )
    return results


def first_text(source: dict[str, Any], *names: str) -> str:
    lower_source = {str(key).lower(): value for key, value in source.items()}
    for name in names:
        value = lower_source.get(name.lower())
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def load_target_entries(path: Path | None) -> list[TargetEntry]:
    if path is None or not path.exists():
        return []
    raw = path.read_text(encoding="utf-8-sig").strip()
    if not raw:
        return []
    if raw.startswith("["):
        payload = json.loads(raw)
        if not isinstance(payload, list):
            raise ValueError(f"{path} must contain a JSON array")
        return [_target_from_json_item(item) for item in payload if _target_from_json_item(item).word]

    entries: list[TargetEntry] = []
    for line in raw.splitlines():
        clean = line.strip()
        if not clean or clean.startswith("#"):
            continue
        word = re.split(r"[\s,\t]+", clean, maxsplit=1)[0].strip()
        if word:
            entries.append(TargetEntry(word=word))
    return entries


def load_wordbook_specs(paths: list[Path]) -> list[WordbookSpec]:
    specs: list[WordbookSpec] = []
    for path in paths:
        raw = path.read_text(encoding="utf-8-sig").strip()
        if not raw:
            continue
        payload = json.loads(raw)
        items = payload if isinstance(payload, list) else [payload]
        for index, item in enumerate(items):
            if not isinstance(item, dict):
                continue
            words_payload = item.get("words") or []
            words = [_wordbook_word_from_item(value, idx) for idx, value in enumerate(words_payload)]
            words = [word for word in words if word.word]
            wordbook_id = first_text(item, "id") or path.stem
            specs.append(
                WordbookSpec(
                    id=wordbook_id,
                    title=first_text(item, "title") or wordbook_id,
                    description=first_text(item, "description"),
                    stage=first_text(item, "stage", "level"),
                    version=first_text(item, "version") or "1.0.0",
                    source=first_text(item, "source") or path.name,
                    sort_order=parse_int(item.get("sortOrder", item.get("sort_order", index)), index),
                    words=words,
                )
            )
    return specs


def _wordbook_word_from_item(item: Any, index: int) -> WordbookWordSpec:
    if isinstance(item, str):
        return WordbookWordSpec(word=item.strip(), sort_order=index)
    if not isinstance(item, dict):
        return WordbookWordSpec(word="", sort_order=index)
    word = first_text(item, "word", "text")
    tags = item.get("tags")
    if isinstance(tags, list):
        tag_values = [str(tag).strip() for tag in tags if str(tag).strip()]
    elif isinstance(tags, str):
        tag_values = [tag.strip() for tag in re.split(r"[,;；\s]+", tags) if tag.strip()]
    else:
        tag_values = []
    return WordbookWordSpec(
        word=word,
        stable_word_id=first_text(item, "id", "stableWordId", "stable_word_id"),
        unit=first_text(item, "unit"),
        level=first_text(item, "level"),
        tags=tag_values,
        priority=parse_int(item.get("priority", 0), 0),
        sort_order=parse_int(item.get("sortOrder", item.get("sort_order", index)), index),
        source=first_text(item, "source"),
    )


def _target_entry_from_membership(spec: WordbookWordSpec, wordbook: WordbookSpec) -> TargetEntry:
    metadata: dict[str, Any] = {
        "id": spec.stable_word_id,
        "word": spec.word,
        "book": wordbook.title,
        "unit": spec.unit,
        "level": spec.level,
        "tags": spec.tags or [],
        "source": spec.source or wordbook.source,
    }
    return TargetEntry(
        word=spec.word,
        id=spec.stable_word_id,
        level=spec.level,
        tags=spec.tags or [],
        metadata=metadata,
    )


def _target_entry_from_current_json(item: dict[str, Any]) -> TargetEntry:
    return TargetEntry(
        word=first_text(item, "word", "text"),
        id=first_text(item, "id"),
        level=first_text(item, "level"),
        tags=item.get("tags") if isinstance(item.get("tags"), list) else None,
        metadata=dict(item),
    )


def _target_from_json_item(item: Any) -> TargetEntry:
    if isinstance(item, str):
        return TargetEntry(word=item.strip())
    if not isinstance(item, dict):
        return TargetEntry(word="")

    word = first_text(item, "word", "text", "id")
    tags = item.get("tags")
    if isinstance(tags, str):
        tags_value = [part.strip() for part in re.split(r"[,;；]", tags) if part.strip()]
    elif isinstance(tags, list):
        tags_value = [str(part).strip() for part in tags if str(part).strip()]
    else:
        tag = first_text(item, "tag")
        tags_value = [part.strip() for part in re.split(r"[,;；\s]+", tag) if part.strip()] if tag else []
    return TargetEntry(
        word=word,
        id=first_text(item, "id"),
        level=first_text(item, "level"),
        tags=tags_value,
        metadata=dict(item),
    )


def load_ecdict_rows(source: Path, target_keys: set[str] | None, limit: int) -> list[dict[str, Any]]:
    if not source.exists():
        raise FileNotFoundError(f"ECDICT source not found: {source}")
    if source.suffix.lower() in SQLITE_SUFFIXES:
        return load_sqlite_rows(source, target_keys=target_keys, limit=limit)
    return load_csv_rows(source, target_keys=target_keys, limit=limit)


def load_csv_rows(source: Path, target_keys: set[str] | None, limit: int) -> list[dict[str, Any]]:
    selected: dict[str, dict[str, Any]] = {}
    common: list[dict[str, Any]] = []
    with source.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            normalized = normalize_row(row)
            word = normalize_word_key(first_text(normalized, "word"))
            if not word:
                continue
            if target_keys:
                if word in target_keys and word not in selected:
                    selected[word] = normalized
                    if len(selected) == len(target_keys):
                        break
            else:
                common.append(normalized)
    if target_keys:
        return list(selected.values())
    return sorted(common, key=row_frequency_key)[:limit]


def load_sqlite_rows(source: Path, target_keys: set[str] | None, limit: int) -> list[dict[str, Any]]:
    conn = sqlite3.connect(source)
    try:
        conn.row_factory = sqlite3.Row
        table, columns = find_word_table(conn)
        if target_keys:
            rows: list[dict[str, Any]] = []
            keys = sorted(target_keys)
            for offset in range(0, len(keys), 400):
                chunk = keys[offset : offset + 400]
                placeholders = ",".join("?" for _ in chunk)
                sql = f'SELECT * FROM "{table}" WHERE lower(word) IN ({placeholders})'
                rows.extend(normalize_row(dict(row)) for row in conn.execute(sql, chunk))
            seen: dict[str, dict[str, Any]] = {}
            for row in rows:
                key = normalize_word_key(first_text(row, "word"))
                if key and key not in seen:
                    seen[key] = row
            return list(seen.values())

        order_by = sqlite_order_by(columns)
        sql = f'SELECT * FROM "{table}" {order_by} LIMIT ?'
        return [normalize_row(dict(row)) for row in conn.execute(sql, (limit,))]
    finally:
        conn.close()


def find_word_table(conn: sqlite3.Connection) -> tuple[str, set[str]]:
    tables = [
        row[0]
        for row in conn.execute("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
    ]
    for table in tables:
        columns = {row[1].lower() for row in conn.execute(f'PRAGMA table_info("{table}")')}
        if "word" in columns:
            return table, columns
    raise ValueError("Could not find an ECDICT SQLite table with a word column")


def sqlite_order_by(columns: set[str]) -> str:
    clauses: list[str] = []
    if "bnc" in columns:
        clauses.append("CASE WHEN bnc IS NULL OR bnc = '' THEN 999999 ELSE CAST(bnc AS INTEGER) END ASC")
    if "frq" in columns:
        clauses.append("CASE WHEN frq IS NULL OR frq = '' THEN 999999 ELSE CAST(frq AS INTEGER) END ASC")
    clauses.append("lower(word) ASC")
    return "ORDER BY " + ", ".join(clauses)


def normalize_row(row: dict[str, Any]) -> dict[str, Any]:
    return {str(key).strip().lower(): value for key, value in row.items() if key is not None}


def row_frequency_key(row: dict[str, Any]) -> tuple[int, int, str]:
    return (
        parse_int(first_text(row, "bnc"), default=999999),
        parse_int(first_text(row, "frq"), default=999999),
        normalize_word_key(first_text(row, "word")),
    )


def parse_int(value: Any, default: int) -> int:
    try:
        text = str(value).strip()
        return int(float(text)) if text else default
    except (TypeError, ValueError):
        return default


def build_word_from_row(
    row: dict[str, Any],
    target: TargetEntry,
    source_name: str,
) -> tuple[dict[str, Any], dict[str, Any] | None, list[dict[str, Any]], list[dict[str, Any]]]:
    word = first_text(row, "word") or target.word
    word_id = target.id or normalize_word_key(word)
    phonetic = first_text(row, "phonetic", "phone", "pronunciation")
    raw_translation = first_text(row, "translation")
    definition = first_text(row, "definition")
    raw_pos = normalize_pos(first_text(row, "pos", "partOfSpeech", "part_of_speech", "wordType", "type"))
    example = first_text(row, "example", "sentence")
    audio = first_text(row, "audio", "audioUrl", "audio_url")

    primary_text = raw_translation or definition
    without_fallback = parse_meaning_items(primary_text, word_id=word_id, fallback_pos="")
    translation_has_pos = any(item.explicit_pos for item in without_fallback)
    if translation_has_pos:
        meaning_items = without_fallback
    else:
        meaning_items = parse_meaning_items(primary_text, word_id=word_id, fallback_pos=raw_pos)

    meanings = [item.to_json(example=example) for item in meaning_items]
    joined_translation = "；".join(item.meaning for item in meaning_items if item.meaning).strip()
    legacy_meaning = meaning_items[0].meaning if meaning_items else (raw_translation or definition)
    first_pos = next((item.pos for item in meaning_items if item.pos), "")

    output: dict[str, Any] = {}
    if target.metadata:
        output.update(
            {
                key: value
                for key, value in target.metadata.items()
                if key
                not in {
                    "phonetic",
                    "audioUrl",
                    "audio_url",
                    "meanings",
                    "definitions",
                    "meaning",
                    "translation",
                    "definition",
                    "pos",
                    "posName",
                    "rawPos",
                    "example",
                    "exampleTranslation",
                    "exampleCn",
                    "source",
                }
            }
        )

    output.update(
        {
            "id": word_id,
            "word": word,
            "phonetic": phonetic,
            "meanings": meanings,
            "meaning": legacy_meaning,
            "translation": joined_translation or raw_translation,
            "definition": definition,
            "pos": first_pos or raw_pos,
            "source": "ecdict",
            "sourceFile": source_name,
        }
    )
    if audio:
        output["audioUrl"] = audio
    if example:
        output["example"] = example
    if target.level:
        output["level"] = target.level
    if target.tags:
        output["tags"] = target.tags
    if raw_pos and raw_pos != output["pos"]:
        output["rawPos"] = raw_pos

    output = {key: value for key, value in output.items() if value not in ("", [], None)}

    conflict = None
    if raw_pos and translation_has_pos:
        parsed_positions = sorted({item.pos for item in meaning_items if item.pos})
        incompatible = [pos for pos in parsed_positions if not pos_compatible(raw_pos, pos)]
        if incompatible:
            conflict = {
                "id": word_id,
                "word": word,
                "rawPos": raw_pos,
                "translationPos": parsed_positions,
                "translation": raw_translation,
                "resolution": "translation_pos_preferred",
            }

    missing_pos = []
    if meanings and not any(str(item.get("pos", "")).strip() for item in meanings):
        missing_pos.append(
            {
                "id": word_id,
                "word": word,
                "meaning": joined_translation or legacy_meaning,
                "reason": "no pos in ECDICT pos field or translation prefix",
            }
        )

    low_quality = low_quality_entries(word_id, word, meanings, raw_translation, definition)
    return output, conflict, missing_pos, low_quality


def pos_compatible(raw_pos: str, parsed_pos: str) -> bool:
    raw = normalize_pos(raw_pos)
    parsed = normalize_pos(parsed_pos)
    if not raw or not parsed or raw == parsed:
        return True
    verb_family = {"v.", "vt.", "vi.", "aux.", "modal."}
    if raw == "v." and parsed in verb_family:
        return True
    return False


def low_quality_entries(
    word_id: str,
    word: str,
    meanings: list[dict[str, Any]],
    raw_translation: str,
    definition: str,
) -> list[dict[str, Any]]:
    entries: list[dict[str, Any]] = []
    if not meanings:
        entries.append(
            {
                "id": word_id,
                "word": word,
                "meaning": raw_translation or definition,
                "reason": "empty meaning",
            }
        )
        return entries
    for meaning in meanings:
        text = str(meaning.get("meaning", "")).strip()
        reasons: list[str] = []
        if not text:
            reasons.append("empty meaning")
        if len(text) > 160:
            reasons.append("meaning too long")
        if MOJIBAKE_RE.search(text):
            reasons.append("possible mojibake")
        if raw_translation == "" and definition and not CJK_RE.search(text):
            reasons.append("no Chinese translation; fell back to English definition")
        if reasons:
            entries.append(
                {
                    "id": word_id,
                    "word": word,
                    "meaningId": meaning.get("id", ""),
                    "meaning": text,
                    "reason": "; ".join(reasons),
                }
            )
    return entries


def import_ecdict(
    source: Path,
    target: Path,
    target_words: Path | None = None,
    report_dir: Path = Path("docs/ecdict-reports"),
    limit: int = 2000,
) -> dict[str, Any]:
    targets = load_target_entries(target_words)
    target_keys = {entry.key for entry in targets if entry.key} or None
    rows = load_ecdict_rows(source, target_keys=target_keys, limit=limit)
    row_by_word = {normalize_word_key(first_text(row, "word")): row for row in rows}

    missing_words: list[dict[str, Any]] = []
    conflicts: list[dict[str, Any]] = []
    missing_pos: list[dict[str, Any]] = []
    low_quality: list[dict[str, Any]] = []
    output_words: list[dict[str, Any]] = []

    if targets:
        iterable_targets = targets
    else:
        iterable_targets = [
            TargetEntry(word=first_text(row, "word"), id=normalize_word_key(first_text(row, "word")))
            for row in rows
        ]

    for target_entry in iterable_targets:
        row = row_by_word.get(target_entry.key)
        if row is None:
            missing_words.append(
                {
                    "id": target_entry.id,
                    "word": target_entry.word,
                    "reason": "target word not found in ECDICT source",
                }
            )
            continue
        output, conflict, word_missing_pos, word_low_quality = build_word_from_row(
            row=row,
            target=target_entry,
            source_name=source.name,
        )
        output_words.append(output)
        if conflict:
            conflicts.append(conflict)
        missing_pos.extend(word_missing_pos)
        low_quality.extend(word_low_quality)

    if targets:
        original_ids = [entry.id for entry in targets if entry.id and entry.key in row_by_word]
        output_ids = [item.get("id", "") for item in output_words if item.get("id")]
        if original_ids and original_ids != output_ids[: len(original_ids)]:
            raise RuntimeError("word.id changed during ECDICT import; aborting write")

    target.parent.mkdir(parents=True, exist_ok=True)
    write_json(target, output_words)
    report_dir.mkdir(parents=True, exist_ok=True)
    write_json(report_dir / "missing-word-report.json", missing_words)
    write_json(report_dir / "missing-pos-report.json", missing_pos)
    write_json(report_dir / "conflict-pos-report.json", conflicts)
    write_json(report_dir / "low-quality-meaning-report.json", low_quality)

    summary = {
        "source": str(source),
        "targetWords": str(target_words) if target_words else "",
        "target": str(target),
        "reportDir": str(report_dir),
        "importedWords": len(output_words),
        "wordsWithPos": sum(
            1
            for item in output_words
            if any(str(meaning.get("pos", "")).strip() for meaning in item.get("meanings", []))
        ),
        "missingWordCount": len(missing_words),
        "missingPosCount": len(missing_pos),
        "conflictPosCount": len(conflicts),
        "lowQualityMeaningCount": len(low_quality),
        "wordIdsChanged": False,
    }
    write_json(report_dir / "import-summary.json", summary)
    return summary


def write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def build_ecdict_sqlite(
    source: Path,
    target_db: Path,
    wordbook_specs: list[WordbookSpec] | None = None,
    limit: int = 0,
) -> dict[str, Any]:
    specs = wordbook_specs or []
    target_keys = {
        word.key
        for spec in specs
        for word in (spec.words or [])
        if word.key
    } or None
    rows = load_ecdict_rows(source, target_keys=target_keys, limit=limit if limit > 0 else 10_000_000)
    target_db.parent.mkdir(parents=True, exist_ok=True)
    if target_db.exists():
        target_db.unlink()
    conn = sqlite3.connect(target_db)
    try:
        create_ecdict_schema(conn)
        for row in rows:
            insert_ecdict_entry(conn, row, source.name)
        for spec in specs:
            insert_wordbook_spec(conn, spec)
        conn.commit()
        missing_memberships = []
        if target_keys:
            found = {normalize_word_key(first_text(row, "word")) for row in rows}
            missing_memberships = [
                {"wordbookId": spec.id, "word": word.word, "wordKey": word.key}
                for spec in specs
                for word in (spec.words or [])
                if word.key and word.key not in found
            ]
        summary = {
            "source": str(source),
            "targetDb": str(target_db),
            "entries": len(rows),
            "wordbooks": len(specs),
            "memberships": sum(len(spec.words or []) for spec in specs),
            "missingMemberships": len(missing_memberships),
        }
        write_json(target_db.with_suffix(".summary.json"), summary)
        if missing_memberships:
            write_json(target_db.with_suffix(".missing-memberships.json"), missing_memberships)
        return summary
    finally:
        conn.close()


def create_ecdict_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE ecdict_entries (
            word_key TEXT PRIMARY KEY,
            word TEXT NOT NULL,
            phonetic TEXT NOT NULL DEFAULT '',
            definition TEXT NOT NULL DEFAULT '',
            translation TEXT NOT NULL DEFAULT '',
            pos TEXT NOT NULL DEFAULT '',
            collins INTEGER NOT NULL DEFAULT 0,
            oxford INTEGER NOT NULL DEFAULT 0,
            tag TEXT NOT NULL DEFAULT '',
            bnc INTEGER NOT NULL DEFAULT 0,
            frq INTEGER NOT NULL DEFAULT 0,
            exchange TEXT NOT NULL DEFAULT '',
            detail TEXT NOT NULL DEFAULT '',
            audio TEXT NOT NULL DEFAULT '',
            source TEXT NOT NULL DEFAULT 'ecdict'
        );
        CREATE INDEX idx_ecdict_entries_word ON ecdict_entries(word);
        CREATE INDEX idx_ecdict_entries_bnc ON ecdict_entries(bnc);
        CREATE INDEX idx_ecdict_entries_frq ON ecdict_entries(frq);

        CREATE TABLE wordbooks (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            stage TEXT NOT NULL DEFAULT '',
            version TEXT NOT NULL DEFAULT '1.0.0',
            source TEXT NOT NULL DEFAULT '',
            sort_order INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE wordbook_words (
            wordbook_id TEXT NOT NULL,
            word_key TEXT NOT NULL,
            stable_word_id TEXT NOT NULL DEFAULT '',
            unit TEXT NOT NULL DEFAULT '',
            level TEXT NOT NULL DEFAULT '',
            tags TEXT NOT NULL DEFAULT '',
            priority INTEGER NOT NULL DEFAULT 0,
            sort_order INTEGER NOT NULL DEFAULT 0,
            source TEXT NOT NULL DEFAULT '',
            PRIMARY KEY(wordbook_id, word_key)
        );
        CREATE INDEX idx_wordbook_words_word_key ON wordbook_words(word_key);
        CREATE INDEX idx_wordbook_words_wordbook_order ON wordbook_words(wordbook_id, sort_order);
        """
    )


def insert_ecdict_entry(conn: sqlite3.Connection, row: dict[str, Any], source_name: str) -> None:
    word = first_text(row, "word")
    word_key = normalize_word_key(word)
    if not word_key:
        return
    conn.execute(
        """
        INSERT OR REPLACE INTO ecdict_entries (
            word_key, word, phonetic, definition, translation, pos, collins, oxford,
            tag, bnc, frq, exchange, detail, audio, source
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            word_key,
            word,
            first_text(row, "phonetic"),
            first_text(row, "definition"),
            first_text(row, "translation"),
            normalize_pos(first_text(row, "pos")),
            parse_int(first_text(row, "collins"), 0),
            parse_int(first_text(row, "oxford"), 0),
            first_text(row, "tag"),
            parse_int(first_text(row, "bnc"), 0),
            parse_int(first_text(row, "frq"), 0),
            first_text(row, "exchange"),
            first_text(row, "detail"),
            first_text(row, "audio"),
            source_name,
        ),
    )


def insert_wordbook_spec(conn: sqlite3.Connection, spec: WordbookSpec) -> None:
    conn.execute(
        """
        INSERT OR REPLACE INTO wordbooks (id, title, description, stage, version, source, sort_order)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (spec.id, spec.title, spec.description, spec.stage, spec.version, spec.source, spec.sort_order),
    )
    for index, word in enumerate(spec.words or []):
        if not word.key:
            continue
        stable_id = word.stable_word_id or f"{spec.id}-{word.key}"
        conn.execute(
            """
            INSERT OR REPLACE INTO wordbook_words (
                wordbook_id, word_key, stable_word_id, unit, level, tags, priority, sort_order, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                spec.id,
                word.key,
                stable_id,
                word.unit,
                word.level,
                ",".join(word.tags or []),
                word.priority,
                word.sort_order if word.sort_order else index,
                word.source or spec.source,
            ),
        )


def import_wordbook_memberships(db_path: Path, wordbook_specs: list[WordbookSpec]) -> dict[str, Any]:
    conn = sqlite3.connect(db_path)
    try:
        for spec in wordbook_specs:
            insert_wordbook_spec(conn, spec)
        conn.commit()
        return {
            "targetDb": str(db_path),
            "wordbooks": len(wordbook_specs),
            "memberships": sum(len(spec.words or []) for spec in wordbook_specs),
        }
    finally:
        conn.close()


def build_wordbook_json_from_sqlite(
    db_path: Path,
    wordbook_id: str,
    target: Path,
    report_dir: Path,
) -> dict[str, Any]:
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    try:
        book_row = conn.execute("SELECT * FROM wordbooks WHERE id = ?", (wordbook_id,)).fetchone()
        if book_row is None:
            raise ValueError(f"Wordbook not found in ECDICT db: {wordbook_id}")
        rows = conn.execute(
            """
            SELECT e.*, ww.stable_word_id, ww.unit, ww.level, ww.tags, ww.priority, ww.sort_order,
                   wb.title AS book_title, wb.source AS book_source
            FROM wordbook_words ww
            INNER JOIN ecdict_entries e ON e.word_key = ww.word_key
            INNER JOIN wordbooks wb ON wb.id = ww.wordbook_id
            WHERE ww.wordbook_id = ?
            ORDER BY ww.sort_order ASC, e.word COLLATE NOCASE ASC
            """,
            (wordbook_id,),
        ).fetchall()

        conflicts: list[dict[str, Any]] = []
        missing_pos: list[dict[str, Any]] = []
        low_quality: list[dict[str, Any]] = []
        words: list[dict[str, Any]] = []
        for row in rows:
            row_dict = dict(row)
            target_entry = TargetEntry(
                word=row_dict.get("word", ""),
                id=row_dict.get("stable_word_id", "") or f"{wordbook_id}-{row_dict.get('word_key', '')}",
                level=row_dict.get("level", ""),
                tags=[part.strip() for part in str(row_dict.get("tags", "")).split(",") if part.strip()],
                metadata={
                    "book": row_dict.get("book_title", ""),
                    "unit": row_dict.get("unit", ""),
                    "source": row_dict.get("book_source", ""),
                    "level": row_dict.get("level", ""),
                    "tags": [part.strip() for part in str(row_dict.get("tags", "")).split(",") if part.strip()],
                },
            )
            output, conflict, word_missing_pos, word_low_quality = build_word_from_row(
                row=row_dict,
                target=target_entry,
                source_name=row_dict.get("source", "ecdict"),
            )
            words.append(output)
            if conflict:
                conflicts.append(conflict)
            missing_pos.extend(word_missing_pos)
            low_quality.extend(word_low_quality)

        target.parent.mkdir(parents=True, exist_ok=True)
        write_json(target, words)
        report_dir.mkdir(parents=True, exist_ok=True)
        write_json(report_dir / "missing-pos-report.json", missing_pos)
        write_json(report_dir / "conflict-pos-report.json", conflicts)
        write_json(report_dir / "low-quality-meaning-report.json", low_quality)
        summary = {
            "sourceDb": str(db_path),
            "wordbookId": wordbook_id,
            "target": str(target),
            "exportedWords": len(words),
            "wordsWithPos": sum(1 for item in words if any(m.get("pos") for m in item.get("meanings", []))),
            "missingPosCount": len(missing_pos),
            "conflictPosCount": len(conflicts),
            "lowQualityMeaningCount": len(low_quality),
        }
        write_json(report_dir / "wordbook-export-summary.json", summary)
        return summary
    finally:
        conn.close()


def read_current_asset_as_wordbook_spec(path: Path, wordbook_id: str, title: str) -> WordbookSpec:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError(f"{path} must contain a JSON array")
    words: list[WordbookWordSpec] = []
    for index, item in enumerate(payload):
        if not isinstance(item, dict):
            continue
        target = _target_entry_from_current_json(item)
        words.append(
            WordbookWordSpec(
                word=target.word,
                stable_word_id=target.id,
                unit=first_text(item, "unit"),
                level=first_text(item, "level"),
                tags=target.tags or [],
                priority=parse_int(item.get("priority", 0), 0),
                sort_order=index,
                source=first_text(item, "source"),
            )
        )
    return WordbookSpec(id=wordbook_id, title=title, source=path.name, words=words)



def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Import a target word bank from a local ECDICT CSV or SQLite file.")
    parser.add_argument("--source", required=True, help="Local ECDICT CSV/SQLite path.")
    parser.add_argument("--target", required=True, help="Output normalized word bank JSON path.")
    parser.add_argument(
        "--target-words",
        default="",
        help="Target word list path. Supports txt lines or JSON array with word/id/level/tags.",
    )
    parser.add_argument(
        "--report-dir",
        default="docs/ecdict-reports",
        help="Directory for missing/conflict/quality reports.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=2000,
        help="Maximum common words to import when no target word list is provided.",
    )
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(list(argv) if argv is not None else None)
    target_words = Path(args.target_words) if args.target_words else None
    summary = import_ecdict(
        source=Path(args.source),
        target=Path(args.target),
        target_words=target_words,
        report_dir=Path(args.report_dir),
        limit=max(1, args.limit),
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0
