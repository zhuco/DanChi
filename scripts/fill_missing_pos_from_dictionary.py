from __future__ import annotations

import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any


WORDBOOK = Path("app/src/main/assets/wordbooks/zhongkao_words.json")
CACHE = Path("docs/dictionaryapi-pos-cache.json")

POS_BY_PART = {
    "noun": "n.",
    "verb": "v.",
    "adjective": "adj.",
    "adverb": "adv.",
    "preposition": "prep.",
    "conjunction": "conj.",
    "pronoun": "pron.",
    "numeral": "num.",
    "article": "art.",
    "interjection": "int.",
    "determiner": "det.",
    "abbreviation": "abbr.",
}

POS_NAMES = {
    "n.": "名词",
    "v.": "动词",
    "adj.": "形容词",
    "adv.": "副词",
    "prep.": "介词",
    "conj.": "连词",
    "pron.": "代词",
    "num.": "数词",
    "art.": "冠词",
    "int.": "感叹词",
    "det.": "限定词",
    "abbr.": "缩写",
    "modal.": "情态动词",
}

EXACT_POS = {
    "a": "art.",
    "an": "art.",
    "the": "art.",
    "i": "pron.",
    "me": "pron.",
    "my": "pron.",
    "mine": "pron.",
    "myself": "pron.",
    "you": "pron.",
    "your": "pron.",
    "yours": "pron.",
    "yourself": "pron.",
    "he": "pron.",
    "him": "pron.",
    "his": "pron.",
    "himself": "pron.",
    "she": "pron.",
    "her": "pron.",
    "hers": "pron.",
    "herself": "pron.",
    "it": "pron.",
    "its": "pron.",
    "itself": "pron.",
    "we": "pron.",
    "us": "pron.",
    "our": "pron.",
    "ours": "pron.",
    "ourselves": "pron.",
    "they": "pron.",
    "them": "pron.",
    "their": "pron.",
    "theirs": "pron.",
    "themselves": "pron.",
    "this": "pron.",
    "that": "pron.",
    "these": "pron.",
    "those": "pron.",
    "what": "pron.",
    "which": "pron.",
    "who": "pron.",
    "whom": "pron.",
    "whose": "pron.",
    "someone": "pron.",
    "somebody": "pron.",
    "anyone": "pron.",
    "anybody": "pron.",
    "everyone": "pron.",
    "everybody": "pron.",
    "nobody": "pron.",
    "nothing": "pron.",
    "something": "pron.",
    "anything": "pron.",
    "one": "num.",
    "two": "num.",
    "three": "num.",
    "four": "num.",
    "five": "num.",
    "six": "num.",
    "seven": "num.",
    "eight": "num.",
    "nine": "num.",
    "ten": "num.",
    "eleven": "num.",
    "twelve": "num.",
    "thirteen": "num.",
    "fourteen": "num.",
    "fifteen": "num.",
    "sixteen": "num.",
    "seventeen": "num.",
    "eighteen": "num.",
    "nineteen": "num.",
    "twenty": "num.",
    "thirty": "num.",
    "forty": "num.",
    "fifty": "num.",
    "sixty": "num.",
    "seventy": "num.",
    "eighty": "num.",
    "ninety": "num.",
    "hundred": "num.",
    "thousand": "num.",
    "first": "num.",
    "second": "num.",
    "third": "num.",
    "january": "n.",
    "february": "n.",
    "march": "n.",
    "april": "n.",
    "may": "n.",
    "june": "n.",
    "july": "n.",
    "august": "n.",
    "september": "n.",
    "october": "n.",
    "november": "n.",
    "december": "n.",
    "monday": "n.",
    "tuesday": "n.",
    "wednesday": "n.",
    "thursday": "n.",
    "friday": "n.",
    "saturday": "n.",
    "sunday": "n.",
    "am": "v.",
    "is": "v.",
    "are": "v.",
    "was": "v.",
    "were": "v.",
    "be": "v.",
    "been": "v.",
    "being": "v.",
    "can": "modal.",
    "could": "modal.",
    "may": "modal.",
    "might": "modal.",
    "must": "modal.",
    "shall": "modal.",
    "should": "modal.",
    "will": "modal.",
    "would": "modal.",
    "and": "conj.",
    "but": "conj.",
    "or": "conj.",
    "because": "conj.",
    "if": "conj.",
    "although": "conj.",
    "though": "conj.",
    "unless": "conj.",
    "whether": "conj.",
    "about": "prep.",
    "above": "prep.",
    "across": "prep.",
    "against": "prep.",
    "along": "prep.",
    "among": "prep.",
    "around": "prep.",
    "at": "prep.",
    "behind": "prep.",
    "below": "prep.",
    "beside": "prep.",
    "between": "prep.",
    "beyond": "prep.",
    "by": "prep.",
    "during": "prep.",
    "except": "prep.",
    "for": "prep.",
    "from": "prep.",
    "in": "prep.",
    "inside": "prep.",
    "into": "prep.",
    "near": "prep.",
    "of": "prep.",
    "off": "prep.",
    "on": "prep.",
    "onto": "prep.",
    "over": "prep.",
    "through": "prep.",
    "to": "prep.",
    "toward": "prep.",
    "under": "prep.",
    "until": "prep.",
    "upon": "prep.",
    "with": "prep.",
    "without": "prep.",
}
EXACT_POS.update({
    word: "adv." for word in (
        "when",
        "why",
        "there",
        "twice",
        "so",
        "then",
        "too",
        "yet",
        "soon",
        "today",
        "yesterday",
        "tomorrow",
        "tonight",
        "sometimes",
        "seldom",
        "very",
        "somewhere",
        "therefore",
        "together",
        "upstairs",
    )
})
EXACT_POS.update({
    word: "prep." for word in (
        "throughout",
        "till",
        "towards",
    )
})
EXACT_POS.update({
    word: "conj." for word in (
        "while",
        "than",
    )
})
EXACT_POS.update({
    word: "num." for word in (
        "sixth",
        "seventh",
        "tenth",
        "zero",
    )
})
EXACT_POS.update({
    word: "pron." for word in (
        "yourselves",
    )
})
EXACT_POS.update({
    word: "v." for word in (
        "seem",
        "set",
        "share",
        "shine",
        "show",
        "sit",
        "smile",
        "stay",
        "succeed",
        "suggest",
        "support",
        "take",
        "talk",
        "throw",
        "travel",
        "try",
        "visit",
        "wash",
        "win",
    )
})
EXACT_POS.update({
    word: "n." for word in (
        "season",
        "seat",
        "secret",
        "service",
        "shop",
        "side",
        "sister",
        "skill",
        "sky",
        "snow",
        "society",
        "son",
        "song",
        "space",
        "sport",
        "spring",
        "station",
        "story",
        "street",
        "student",
        "summer",
        "sun",
        "supermarket",
        "teacher",
        "team",
        "television",
        "term",
        "test",
        "theatre",
        "thing",
        "ticket",
        "time",
        "tooth",
        "town",
        "tree",
        "trip",
        "trouble",
        "uncle",
        "university",
        "village",
        "visitor",
        "voice",
        "wall",
        "war",
        "way",
        "weather",
        "week",
        "weekend",
        "wife",
        "wind",
        "window",
        "winter",
        "woman",
        "word",
        "worker",
        "world",
        "writer",
        "year",
        "christmas",
        "sea",
        "sentence",
        "sheep",
        "shoe",
        "shoulder",
        "snake",
        "sock",
        "sofa",
        "soldier",
        "soup",
        "spoon",
        "stamp",
        "star",
        "stomach",
        "stone",
        "store",
        "stranger",
        "sweater",
        "symbol",
        "taxi",
        "tea",
        "temperature",
        "text",
        "throat",
        "tomato",
        "tower",
        "tradition",
        "tshirt",
        "tshirtt",
        "umbrella",
        "video",
        "violin",
        "wallet",
        "website",
        "whale",
        "winner",
        "zoo",
        "asia",
        "france",
        "germany",
        "india",
        "russia",
        "africa",
        "america",
        "australia",
        "canada",
        "europe",
        "england",
        "britain",
        "london",
        "beijing",
        "shanghai",
        "hongkong",
        "newyork",
        "paris",
        "rome",
        "tokyo",
        "silence",
        "situation",
        "speech",
        "speed",
        "spirit",
        "success",
        "surface",
        "task",
        "technology",
        "teenager",
        "tool",
        "truth",
        "value",
        "victory",
        "weight",
        "wheel",
    )
})

VERB_HINTS = (
    "买",
    "卖",
    "做",
    "制造",
    "使",
    "让",
    "去",
    "来",
    "到达",
    "离开",
    "进入",
    "返回",
    "开始",
    "结束",
    "打开",
    "关闭",
    "写",
    "读",
    "看",
    "听",
    "说",
    "告诉",
    "问",
    "回答",
    "叫",
    "喊",
    "跑",
    "走",
    "跳",
    "游泳",
    "飞",
    "骑",
    "驾驶",
    "吃",
    "喝",
    "睡",
    "学习",
    "教",
    "工作",
    "帮助",
    "使用",
    "练习",
    "喜欢",
    "爱",
    "讨厌",
    "想",
    "认为",
    "知道",
    "理解",
    "记得",
    "忘记",
    "发现",
    "找到",
    "带来",
    "带走",
    "穿",
    "花费",
    "付",
    "发送",
    "收到",
    "邀请",
    "决定",
    "改变",
    "保持",
    "移动",
    "跟随",
    "等待",
    "希望",
    "需要",
)


def normalize_word(word: str) -> str:
    return re.sub(r"[^a-z]", "", word.lower())


def has_pos(item: dict[str, Any]) -> bool:
    if str(item.get("pos", "")).strip():
        return True
    for meaning in item.get("meanings") or []:
        if isinstance(meaning, dict) and str(meaning.get("pos", "")).strip():
            return True
    return False


def infer_local_pos(word: str, meaning: str) -> str:
    key = normalize_word(word)
    if key in EXACT_POS:
        return EXACT_POS[key]
    if key.endswith("ly") or "地" in meaning:
        return "adv."
    if any(key.endswith(suffix) for suffix in ("ful", "less", "ous", "ive", "able", "ible", "al", "ic")):
        return "adj."
    if meaning.endswith("的") or "的；" in meaning or "的，" in meaning or "的、" in meaning:
        return "adj."
    if any(meaning.startswith(hint) for hint in VERB_HINTS):
        return "v."
    return ""


def choose_pos(word: str, meaning: str, api_parts: list[str]) -> str:
    local = infer_local_pos(word, meaning)
    if local:
        return local
    api_pos = [POS_BY_PART.get(part.lower(), "") for part in api_parts]
    api_pos = [pos for pos in api_pos if pos]
    if not api_pos:
        return ""
    if "v." in api_pos and any(meaning.startswith(hint) for hint in VERB_HINTS):
        return "v."
    if "adj." in api_pos and ("的" in meaning or meaning.endswith(("able", "ful", "less"))):
        return "adj."
    return api_pos[0]


def fetch_parts(word: str) -> list[str]:
    query = urllib.parse.quote(word.lower())
    request = urllib.request.Request(
        f"https://api.dictionaryapi.dev/api/v2/entries/en/{query}",
        headers={"User-Agent": "Mozilla/5.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as exc:
        if exc.code in (403, 404, 429):
            return []
        raise
    except Exception:
        return []
    if not isinstance(payload, list) or not payload:
        return []
    parts: list[str] = []
    for entry in payload:
        if not isinstance(entry, dict):
            continue
        for meaning in entry.get("meanings", []):
            if not isinstance(meaning, dict):
                continue
            part = str(meaning.get("partOfSpeech", "")).strip().lower()
            if part and part not in parts:
                parts.append(part)
    return parts


def load_cache() -> dict[str, list[str]]:
    if not CACHE.exists():
        return {}
    return json.loads(CACHE.read_text(encoding="utf-8"))


def save_cache(cache: dict[str, list[str]]) -> None:
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def set_pos(item: dict[str, Any], pos: str) -> None:
    item["pos"] = pos
    meanings = item.get("meanings")
    if not isinstance(meanings, list) or not meanings:
        item["meanings"] = [
            {
                "id": f"{item.get('id') or item.get('word')}-meaning-0",
                "pos": pos,
                "posName": POS_NAMES.get(pos, ""),
                "meaning": item.get("meaning", ""),
            }
        ]
        return
    for meaning in meanings:
        if not isinstance(meaning, dict):
            continue
        if not str(meaning.get("pos", "")).strip():
            meaning["pos"] = pos
            meaning["posName"] = POS_NAMES.get(pos, "")
            return


def main() -> int:
    parser = argparse.ArgumentParser(description="Fill missing POS fields in the local wordbook.")
    parser.add_argument("--offline", action="store_true", help="Use only local rules and cached API results.")
    parser.add_argument(
        "--allow-heuristic-write",
        action="store_true",
        help="Deprecated compatibility switch. Allows the old heuristic script to modify the main wordbook.",
    )
    args = parser.parse_args()

    if not args.allow_heuristic_write:
        print(json.dumps({
            "error": "This legacy script uses heuristic POS inference and is disabled by default.",
            "use": "python scripts/import-ecdict.py --source data/ecdict.csv --target app/src/main/assets/wordbooks/zhongkao_words.json --target-words app/src/main/assets/wordbooks/zhongkao_words.json",
            "reason": "POS must come from ECDICT fields or explicit translation prefixes; uncertain items belong in reports.",
        }, ensure_ascii=False, indent=2))
        return 2

    words = json.loads(WORDBOOK.read_text(encoding="utf-8"))
    cache = load_cache()
    missing = [
        item for item in words
        if isinstance(item, dict) and str(item.get("word", "")).strip() and not has_pos(item)
    ]

    if not args.offline:
        needed = sorted({normalize_word(item["word"]) for item in missing if not infer_local_pos(item["word"], item.get("meaning", ""))})
        needed = [word for word in needed if word and word not in cache]
        with ThreadPoolExecutor(max_workers=2) as executor:
            futures = {executor.submit(fetch_parts, word): word for word in needed}
            for index, future in enumerate(as_completed(futures), start=1):
                word = futures[future]
                cache[word] = future.result()
                if index % 20 == 0:
                    save_cache(cache)
                    time.sleep(1.0)
        save_cache(cache)

    filled = 0
    by_source = {"local": 0, "dictionary": 0}
    still_missing: list[str] = []
    for item in missing:
        word = item["word"]
        meaning = str(item.get("meaning", "")).strip()
        local = infer_local_pos(word, meaning)
        api_parts = cache.get(normalize_word(word), [])
        pos = choose_pos(word, meaning, api_parts)
        if pos:
            set_pos(item, pos)
            filled += 1
            by_source["local" if local else "dictionary"] += 1
        else:
            still_missing.append(word)

    WORDBOOK.write_text(json.dumps(words, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "missingBefore": len(missing),
        "filled": filled,
        "bySource": by_source,
        "missingAfter": len(still_missing),
        "sampleMissing": still_missing[:30],
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
