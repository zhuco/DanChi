from __future__ import annotations

import json
import pathlib
import re
from collections import Counter, OrderedDict
from datetime import datetime


ROOT = pathlib.Path(__file__).resolve().parents[1]
TXT = ROOT / "\u521d\u4e2d\u4e2d\u8003.txt"
JSON_PATH = ROOT / "app/src/main/assets/wordbooks/zhongkao_words.json"
REPORT = ROOT / "docs" / "\u8bcd\u5e93\u5f02\u5e38\u62a5\u544a.md"

BOOK_NAME = "\u521d\u4e2d\u4e2d\u8003"
SOURCE_NAME = "\u521d\u4e2d\u4e2d\u8003.txt"

DROP_MEANINGS = {
    "unconditional": {"\u4eba\u4e0d\u8212\u670d\u7684"},
    "energetic": {"\u7535\u5b50\u7684"},
    "lonely": {"\u7f8e\u597d\u7684\uff0c\u53ef\u7231\u7684"},
    "modern": {
        "\u6d3b\u52a8\u7684\uff1b\u6613\u4e8e\u79fb\u52a8\u7684",
        "\u6d3b\u52a8\u7684 \u6613\u4e8e\u79fb\u52a8\u7684",
        "\u6d3b\u52a8\u7684",
        "\u6613\u4e8e\u79fb\u52a8\u7684",
    },
}

WORD_MEANING_FIXES = {
    ("unconditional", "\u65e0\u6761\u4ef6\uff0c\u7edd\u5bf9\u7684"): "\u65e0\u6761\u4ef6\u7684\uff0c\u7edd\u5bf9\u7684",
    ("comb", "\u68b3\uff08\u53d1\uff09\u770b\uff0c\u641c\u7d22"): "\u68b3\uff08\u53d1\uff09\uff0c\u641c\u67e5",
    ("attack", "\u653b\u51fb \u62a8\u51fb"): "\u653b\u51fb\uff1b\u62a8\u51fb",
    ("careful", "\u5c0f\u5fc3\u7684\uff0c\u81ea\u4fe1\u7684"): "\u5c0f\u5fc3\u7684",
    ("careful", "\u5c0f\u5fc3\u7684\uff0c \u81ea\u4fe1\u7684"): "\u5c0f\u5fc3\u7684",
    ("fine", "\u7ec6\u6674\u6717\u7684\u7f8e\u597d\u7684"): "\u7ec6\u7684\uff1b\u6674\u6717\u7684\uff1b\u7f8e\u597d\u7684",
    ("cross", "\u8d8a\u8fc7\uff0c\u7a7f\u8fc7\u5341\u5b57\u67b6"): "\u8d8a\u8fc7\uff0c\u7a7f\u8fc7\uff1b\u5341\u5b57\u67b6",
    ("desert", "\u820d\u5f03\uff0c\u9057\u5f03 \u6c99\u6f20"): "\u820d\u5f03\uff0c\u9057\u5f03\uff1b\u6c99\u6f20",
    ("tie", "\uff08\u7528\u7ef3\uff0c\u95f2\uff09\u7cfb\uff0c\u6813\uff0c\u624e"): "\uff08\u7528\u7ef3\uff0c\u7ebf\uff09\u7cfb\uff0c\u6813\uff0c\u624e",
    ("operate", "\u7ecf \u8425"): "\u7ecf\u8425",
    ("chant", "\u53cd\u590d\u547c\u558a \u541f \u5531"): "\u53cd\u590d\u547c\u558a\uff1b\u541f\u5531",
}

TEXT_FIXES = {
    "\u5224 \u65ad": "\u5224\u65ad",
    "\u5224 \u5b9a": "\u5224\u5b9a",
    "\u4fdd \u8bc1": "\u4fdd\u8bc1",
    "\u62c5 \u4fdd": "\u62c5\u4fdd",
    "\u505c \u653e": "\u505c\u653e",
    "\u6c7d \u8f66": "\u6c7d\u8f66",
    "\u7535 \u5b50 \u7684": "\u7535\u5b50\u7684",
    "\u5c0f \u5fc3 \u7684": "\u5c0f\u5fc3\u7684",
    "\u81ea \u4fe1 \u7684": "\u81ea\u4fe1\u7684",
    "\u666e \u901a \u7684": "\u666e\u901a\u7684",
    "\u5e73 \u5e38 \u7684": "\u5e73\u5e38\u7684",
    "\u80fd \u591f": "\u80fd\u591f",
    "\u6709 \u80fd \u529b \u7684": "\u6709\u80fd\u529b\u7684",
}

PUNCT_SPACE_RX = re.compile(r"\s+([\u3001\uff0c\u3002\uff1b\uff1a\uff1f\uff01\uff09])")
OPEN_PAREN_SPACE_RX = re.compile(r"\uff08\s+")
CLOSE_PAREN_SPACE_RX = re.compile(r"\s+\uff09")
MULTI_SPACE_RX = re.compile(r"[\t ]{2,}")
ELLIPSIS_RX = re.compile(r"\.{3,}")
MERGE_SEP_RX = re.compile(r"\s*\uff1b\s*")
REPEAT_SEP_RX = re.compile(r"\uff1b{2,}")
CJK_SPACE_RX = re.compile(r"[\u4e00-\u9fff]\s+[\u4e00-\u9fff]")
CJK_SPACE_TO_SEP_RX = re.compile(r"(?<=[\u4e00-\u9fff\uff09])\s+(?=[\u4e00-\u9fff\uff08])")


def normalize_meaning(raw: str, word: str) -> tuple[str, bool]:
    original = raw.strip()
    text = MULTI_SPACE_RX.sub("\uff1b", original)
    for old, new in TEXT_FIXES.items():
        text = text.replace(old, new)
    text = ELLIPSIS_RX.sub("\u2026\u2026", text)
    text = PUNCT_SPACE_RX.sub(r"\1", text)
    text = OPEN_PAREN_SPACE_RX.sub("\uff08", text)
    text = CLOSE_PAREN_SPACE_RX.sub("\uff09", text)
    text = MERGE_SEP_RX.sub("\uff1b", text)
    text = REPEAT_SEP_RX.sub("\uff1b", text)
    text = re.sub(r"\s+", " ", text).strip()
    text = WORD_MEANING_FIXES.get((word.lower(), text), text)
    text = CJK_SPACE_TO_SEP_RX.sub("\uff1b", text)
    text = REPEAT_SEP_RX.sub("\uff1b", text)
    text = WORD_MEANING_FIXES.get((word.lower(), text), text)
    return text, text != original


def split_meaning_parts(meaning: str) -> list[str]:
    parts = []
    for part in re.split(r"[\uff1b;]", meaning):
        clean = part.strip(" \u3001\uff0c,;\uff1b")
        if clean:
            parts.append(clean)
    return parts


def parse_entry(line: str) -> tuple[str, str] | None:
    stripped = line.strip()
    if not stripped or not re.match(r"^[A-Za-z]", stripped):
        return None

    first_chinese = None
    for idx, ch in enumerate(stripped):
        if "\u4e00" <= ch <= "\u9fff":
            first_chinese = idx
            break
    if first_chinese is None:
        return None

    head_raw = stripped[:first_chinese].strip()
    meaning = stripped[first_chinese:].strip()
    if head_raw.endswith(("\uff08", "(")):
        match = re.match(r"^([A-Za-z][A-Za-z' -]*)(?:\s*[\uff08(])", stripped)
        if match:
            head_raw = match.group(1).strip()
            meaning = stripped[match.end(1) :].strip()

    head = head_raw.strip(" ,\uff0c;\uff1b:\uff1a()\uff08\uff09")
    if not re.fullmatch(r"[A-Za-z][A-Za-z' -]*", head) and "\u2026" in head:
        match = re.match(r"^([A-Za-z][A-Za-z' -]*)", stripped)
        if match:
            head = match.group(1).strip()
            meaning = stripped[match.end(1) :].strip()
    if not head:
        return None
    word = re.split(r"\s*,\s*|\s{2,}", head)[0].strip()
    if not re.fullmatch(r"[A-Za-z][A-Za-z' -]*", word):
        return None
    return word, meaning


def stable_id(word: str, index: int) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", word.strip().lower()).strip("-")
    return f"zk-{index:04d}-{normalized or 'word'}"


def main() -> None:
    source_text = TXT.read_text(encoding="utf-8")
    source_lines = source_text.splitlines()
    old_json = json.loads(JSON_PATH.read_text(encoding="utf-8"))

    backup_dir = ROOT / "docs" / ("wordbook_clean_backup_" + datetime.now().strftime("%Y%m%d_%H%M%S"))
    backup_dir.mkdir(parents=True, exist_ok=True)
    (backup_dir / TXT.name).write_bytes(TXT.read_bytes())
    (backup_dir / JSON_PATH.name).write_bytes(JSON_PATH.read_bytes())

    entries: OrderedDict[str, dict] = OrderedDict()
    old_words = {item["word"].lower() for item in old_json}
    unit = "\u7efc\u5408"
    source_entry_count = 0
    format_fix_count = 0
    dropped_count = 0
    duplicate_rows = []
    parse_skipped = []
    added_missing_words = []

    for line_no, raw in enumerate(source_lines, 1):
        if not raw.strip():
            continue
        if not re.match(r"^[A-Za-z]", raw.strip()):
            unit = re.sub(r"\s+", " ", raw.strip())
            continue

        parsed = parse_entry(raw)
        if not parsed:
            parse_skipped.append((line_no, raw.strip()))
            continue
        source_entry_count += 1
        word, raw_meaning = parsed
        key = word.lower()
        meaning, changed = normalize_meaning(raw_meaning, word)
        if changed:
            format_fix_count += 1

        kept_parts = []
        for part in split_meaning_parts(meaning):
            if part in DROP_MEANINGS.get(key, set()):
                dropped_count += 1
            else:
                kept_parts.append(part)
        if not kept_parts:
            duplicate_rows.append((key, line_no, meaning, "all_parts_dropped"))
            continue

        if key not in entries:
            entries[key] = {
                "word": word,
                "unit": unit,
                "first_line": line_no,
                "meanings": [],
                "source_rows": [],
            }
            if key not in old_words:
                added_missing_words.append((word, line_no, meaning))
        else:
            duplicate_rows.append((key, line_no, meaning, "merged"))

        for part in kept_parts:
            if part not in entries[key]["meanings"]:
                entries[key]["meanings"].append(part)
        entries[key]["source_rows"].append(line_no)

    items = []
    for idx, data in enumerate(entries.values(), 1):
        word = data["word"]
        items.append(
            OrderedDict(
                [
                    ("id", stable_id(word, idx)),
                    ("word", word),
                    ("meaning", "\uff1b".join(data["meanings"])),
                    ("book", BOOK_NAME),
                    ("unit", data["unit"]),
                    ("source", SOURCE_NAME),
                    ("sourceLine", data["first_line"]),
                ]
            )
        )

    unit_groups: OrderedDict[str, list[dict]] = OrderedDict()
    for data in entries.values():
        unit_groups.setdefault(data["unit"], []).append(data)

    clean_lines = []
    for unit_name, rows in unit_groups.items():
        if clean_lines:
            clean_lines.append("")
        clean_lines.append(unit_name)
        for data in rows:
            clean_lines.append(f"{data['word']} {'\uff1b'.join(data['meanings'])}")

    TXT.write_text("\n".join(clean_lines) + "\n", encoding="utf-8", newline="\n")
    JSON_PATH.write_text(json.dumps(items, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")

    word_counts = Counter(item["word"].lower() for item in items)
    duplicates_after = [word for word, count in word_counts.items() if count > 1]
    space_issues = [(item["word"], item["meaning"]) for item in items if CJK_SPACE_RX.search(item["meaning"])]
    suspicious_merge = []
    suspicious_meaning = []
    for item in items:
        parts = split_meaning_parts(item["meaning"])
        if len(parts) >= 6:
            suspicious_merge.append((item["word"], item["meaning"], len(parts)))
        if any(
            token in item["meaning"]
            for token in [
                "\u4eba\u4e0d\u8212\u670d",
                "\u7535\u5b50\u7684\uff1b\u7cbe\u529b",
                "\u7f8e\u597d\uff0c\u53ef\u7231\u7684\uff1b\u5b64\u72ec",
                "\u7ec6\u6674\u6717",
            ]
        ):
            suspicious_meaning.append((item["word"], item["meaning"]))

    manual_candidates: OrderedDict[str, dict] = OrderedDict()

    def add_manual(word: str, reason: str, meaning: str = "") -> None:
        manual_candidates.setdefault(word, {"reasons": [], "meaning": meaning})
        if reason not in manual_candidates[word]["reasons"]:
            manual_candidates[word]["reasons"].append(reason)
        if meaning and not manual_candidates[word]["meaning"]:
            manual_candidates[word]["meaning"] = meaning

    manual_reasons = {
        "cross": "\u540c\u65f6\u542b\u52a8\u8bcd\u3001\u540d\u8bcd\u548c\u5f62\u5bb9\u8bcd\u4e49\uff0c\u9700\u786e\u8ba4\u662f\u5426\u4fdd\u7559\u5728\u540c\u4e00\u8bcd\u6761\u3002",
        "present": "\u5408\u5e76\u4e86\u52a8\u8bcd\u3001\u5f62\u5bb9\u8bcd\u91ca\u4e49\uff0c\u4e14\u201c\u5f15\u89c1\u201d\u662f\u5426\u7b26\u5408\u4e2d\u8003\u5e38\u7528\u4e49\u9700\u786e\u8ba4\u3002",
        "subject": "\u5408\u5e76\u4e86\u540d\u8bcd\u3001\u5f62\u5bb9\u8bcd\u3001\u52a8\u8bcd\u4e49\uff0c\u52a8\u8bcd\u4e49\u7531\u6b64\u524d\u6f0f\u89e3\u6790\u7684\u201c\uff08\u4f7f\uff09\u201d\u884c\u6062\u590d\u3002",
        "fine": "\u5df2\u4fee\u6b63\u660e\u663e\u7c98\u8fde\uff0c\u4f46 fine \u8003\u8bd5\u4e49\u8f83\u591a\uff0c\u9700\u786e\u8ba4\u6392\u5e8f\u3002",
        "plain": "\u201c\u5bb6\u5e38\u7684\u201d\u7591\u4f3c\u504f\u79bb\u4e2d\u8003\u5e38\u7528\u4e49\uff0c\u9700\u786e\u8ba4\u662f\u5426\u6539\u4e3a\u201c\u666e\u901a\u7684\uff1b\u6734\u7d20\u7684\u201d\u3002",
        "desert": "\u540c\u65f6\u542b\u52a8\u8bcd\u201c\u9057\u5f03\u201d\u548c\u540d\u8bcd\u201c\u6c99\u6f20\u201d\uff0c\u9700\u786e\u8ba4\u662f\u5426\u4fdd\u7559\u5408\u5e76\u3002",
        "phone": "\u6e90\u884c\u542b phone, telephone\uff0c\u5f53\u524d\u53ea\u4ee5 phone \u5efa\u8bcd\uff0ctelephone \u53e6\u6709\u72ec\u7acb\u8bcd\u6761\u3002",
    }
    for word, reason in manual_reasons.items():
        item = next((x for x in items if x["word"].lower() == word), None)
        add_manual(word, reason, item["meaning"] if item else "")
    for word, meaning, count in suspicious_merge[:40]:
        add_manual(
            word,
            f"\u5408\u5e76\u91ca\u4e49\u9879\u8f83\u591a\uff08{count} \u9879\uff09\uff0c\u5efa\u8bae\u4eba\u5de5\u786e\u8ba4\u662f\u5426\u8fc7\u5bbd\u3002",
            meaning,
        )
    for word, meaning in space_issues[:40]:
        add_manual(word, "\u4ecd\u5b58\u5728\u4e2d\u6587\u95f4\u7a7a\u683c\uff0c\u9700\u4eba\u5de5\u5224\u65ad\u662f\u7f3a\u6807\u70b9\u8fd8\u662f\u8bcd\u5185\u7a7a\u683c\u3002", meaning)
    for word, meaning in suspicious_meaning:
        add_manual(word, "\u4ecd\u547d\u4e2d\u7591\u4f3c\u9519\u4e49\u5173\u952e\u8bcd\u3002", meaning)

    report_lines = [
        "# \u8bcd\u5e93\u5f02\u5e38\u62a5\u544a",
        "",
        f"- \u751f\u6210\u65f6\u95f4\uff1a{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"- \u5907\u4efd\u76ee\u5f55\uff1a`{backup_dir}`",
        f"- \u6e90 txt \u539f\u59cb\u53ef\u89e3\u6790\u8bcd\u6761\u884c\uff1a{source_entry_count}",
        f"- \u65e7 JSON \u8bcd\u6761\u6570\uff1a{len(old_json)}",
        f"- \u6e05\u6d17\u540e\u552f\u4e00\u8bcd\u6761\u6570\uff1a{len(items)}",
        f"- \u6e90 txt \u91cd\u590d\u8bcd\u884c\uff1a{len(duplicate_rows)}\uff0c\u5df2\u5408\u5e76\u5230\u9996\u6b21\u51fa\u73b0\u8bcd\u6761",
        f"- \u683c\u5f0f/\u7a7a\u683c/\u7701\u7565\u53f7\u81ea\u52a8\u4fee\u590d\u884c\uff1a{format_fix_count}",
        f"- \u9ad8\u7f6e\u4fe1\u9519\u4e49\u5220\u9664\u9879\uff1a{dropped_count}",
        f"- \u65e7\u8f6c\u6362\u5668\u6f0f\u6536\u3001\u73b0\u5df2\u6062\u590d\u8bcd\u6761\uff1a{len(added_missing_words)}",
        f"- \u6e05\u6d17\u540e\u91cd\u590d word\uff1a{len(duplicates_after)}",
        f"- \u6e05\u6d17\u540e\u4e2d\u6587\u95f4\u7a7a\u683c\u6b8b\u7559\uff1a{len(space_issues)}",
        "",
        "## \u81ea\u52a8\u4fee\u590d\u6458\u8981",
        "",
        "- \u5408\u5e76\u91cd\u590d word\uff0c\u5e76\u53bb\u6389\u5b8c\u5168\u91cd\u590d\u7684\u91ca\u4e49\u7247\u6bb5\u3002",
        "- \u4fee\u590d\u91ca\u4e49\u5f00\u5934\u4e3a\u201c\uff08\u4f7f\uff09\u201d\u5bfc\u81f4\u8f6c\u6362\u5668\u6f0f\u8bcd\u7684\u95ee\u9898\u3002",
        "- \u7edf\u4e00\u591a\u4f59\u7a7a\u683c\u3001\u5217\u5f0f\u7a7a\u767d\u3001\u7701\u7565\u53f7\u548c\u5206\u53f7\u5206\u9694\u3002",
        "- \u5220\u9664\u9ad8\u7f6e\u4fe1\u9519\u4e49\uff1a`unconditional`\u3001`energetic`\u3001`lonely`\u3001`modern` \u4e2d\u7684\u660e\u663e\u9519\u91ca\u7247\u6bb5\u3002",
        "- \u4fee\u6b63\u660e\u663e\u7c98\u8fde/\u9519\u5b57\uff1a`comb`\u3001`attack`\u3001`careful`\u3001`fine`\u3001`cross`\u3001`desert` \u7b49\u3002",
        "",
        "## \u4ecd\u9700\u4eba\u5de5\u786e\u8ba4",
        "",
    ]

    if manual_candidates:
        report_lines.extend(["| word | \u5f53\u524d\u91ca\u4e49 | \u539f\u56e0 |", "| --- | --- | --- |"])
        for word, info in list(manual_candidates.items())[:80]:
            meaning = info["meaning"].replace("|", "\\|")
            reason = "\uff1b".join(info["reasons"]).replace("|", "\\|")
            report_lines.append(f"| `{word}` | {meaning} | {reason} |")
    else:
        report_lines.append("- \u6682\u65e0\u3002")

    report_lines.extend(["", "## \u65e7\u8f6c\u6362\u5668\u6f0f\u6536\u8bcd\u6761\uff08\u5df2\u6062\u590d\uff09", ""])
    if added_missing_words:
        report_lines.extend(["| word | \u6e90\u884c | \u91ca\u4e49 |", "| --- | ---: | --- |"])
        for word, line_no, meaning in added_missing_words[:120]:
            report_lines.append(f"| `{word}` | {line_no} | {meaning} |")
    else:
        report_lines.append("- \u65e0\u3002")

    report_lines.extend(["", "## \u89e3\u6790\u8df3\u8fc7\u884c", ""])
    if parse_skipped:
        report_lines.extend(["| \u6e90\u884c | \u5185\u5bb9 |", "| ---: | --- |"])
        for line_no, content in parse_skipped[:120]:
            report_lines.append(f"| {line_no} | {content.replace('|', '\\|')} |")
    else:
        report_lines.append("- \u65e0\u3002")

    REPORT.write_text("\n".join(report_lines) + "\n", encoding="utf-8", newline="\n")

    print(
        json.dumps(
            {
                "backup_dir": str(backup_dir),
                "old_json_count": len(old_json),
                "new_count": len(items),
                "source_entry_count": source_entry_count,
                "duplicate_rows_merged": len(duplicate_rows),
                "format_fix_count": format_fix_count,
                "dropped_wrong_meanings": dropped_count,
                "added_missing_words_count": len(added_missing_words),
                "duplicates_after": len(duplicates_after),
                "space_issues_after": len(space_issues),
                "manual_candidates": len(manual_candidates),
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
