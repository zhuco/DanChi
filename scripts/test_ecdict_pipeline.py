from __future__ import annotations

import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from ecdict_pipeline import (
    WordbookSpec,
    WordbookWordSpec,
    build_ecdict_sqlite,
    build_wordbook_json_from_sqlite,
    import_ecdict,
    normalize_pos,
    parse_meaning_text,
)


class EcdictPipelineTest(unittest.TestCase):
    def test_parse_meaning_text_variants(self) -> None:
        self.assertEqual(parse_meaning_text("n. 苹果", "apple")[0]["pos"], "n.")
        self.assertEqual(parse_meaning_text("n.苹果", "apple")[0]["meaning"], "苹果")
        self.assertEqual(parse_meaning_text("n: 苹果", "apple")[0]["pos"], "n.")
        self.assertEqual(parse_meaning_text("vt. 应用；涂", "apply")[0]["pos"], "vt.")
        self.assertEqual(parse_meaning_text("名词 苹果", "apple")[0]["pos"], "n.")

        multi = parse_meaning_text("v. 申请；应用；vt. 应用；涂\nvi. 申请；适用", "apply")
        self.assertEqual([item["pos"] for item in multi], ["v.", "vt.", "vi."])
        self.assertEqual(multi[0]["meaning"], "申请；应用")

        no_pos = parse_meaning_text("苹果", "apple")
        self.assertEqual(no_pos[0]["pos"], "")
        self.assertEqual(no_pos[0]["meaning"], "苹果")

    def test_imports_target_words_and_reports(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "ecdict.csv"
            source.write_text(
                "\n".join(
                    [
                        "word,phonetic,translation,pos,definition,example,bnc,frq",
                        'apple,/ˈæpəl/,n. 苹果；苹果树,n,apple definition,An apple is red.,1,1',
                        'apply,/əˈplaɪ/,"v. 申请；应用\nvt. 应用；涂\nvi. 申请；适用",v,apply definition,,2,2',
                        "book,/bʊk/,书；本子,n,book definition,,3,3",
                        "object,/ˈɒbdʒekt/,n. 物体,v,object definition,,4,4",
                        "unknown,,未知释义,,unknown definition,,5,5",
                    ]
                ),
                encoding="utf-8",
            )
            target_words = root / "targets.json"
            target_words.write_text(
                json.dumps(
                    [
                        {"id": "existing-apple-id", "word": "apple", "level": "primary", "tags": ["exam"]},
                        {"id": "existing-apply-id", "word": "apply"},
                        {"id": "existing-book-id", "word": "book"},
                        {"id": "existing-object-id", "word": "object"},
                        {"id": "existing-unknown-id", "word": "unknown"},
                        {"id": "existing-missing-id", "word": "missing"},
                    ],
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            target = root / "words.normalized.json"
            report_dir = root / "reports"

            summary = import_ecdict(source, target, target_words, report_dir)
            words = json.loads(target.read_text(encoding="utf-8"))

            self.assertEqual(summary["importedWords"], 5)
            self.assertEqual(words[0]["id"], "existing-apple-id")
            self.assertEqual(words[0]["level"], "primary")
            self.assertEqual(words[0]["tags"], ["exam"])
            self.assertEqual(words[0]["meanings"][0]["pos"], "n.")
            self.assertEqual(words[0]["meanings"][0]["meaning"], "苹果；苹果树")

            apply = next(item for item in words if item["word"] == "apply")
            self.assertEqual([item["pos"] for item in apply["meanings"]], ["v.", "vt.", "vi."])

            book = next(item for item in words if item["word"] == "book")
            self.assertEqual(book["meanings"][0]["pos"], "n.")
            self.assertEqual(book["meanings"][0]["meaning"], "书；本子")

            conflicts = json.loads((report_dir / "conflict-pos-report.json").read_text(encoding="utf-8"))
            self.assertEqual(conflicts[0]["word"], "object")
            self.assertEqual(conflicts[0]["rawPos"], "v.")

            missing_pos = json.loads((report_dir / "missing-pos-report.json").read_text(encoding="utf-8"))
            self.assertEqual(missing_pos[0]["word"], "unknown")

            missing_words = json.loads((report_dir / "missing-word-report.json").read_text(encoding="utf-8"))
            self.assertEqual(missing_words[0]["word"], "missing")

    def test_imports_from_sqlite(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "ecdict.sqlite"
            conn = sqlite3.connect(source)
            try:
                conn.execute(
                    "CREATE TABLE stardict (word TEXT, phonetic TEXT, translation TEXT, pos TEXT, definition TEXT, bnc INTEGER, frq INTEGER)"
                )
                conn.execute(
                    "INSERT INTO stardict VALUES (?, ?, ?, ?, ?, ?, ?)",
                    ("run", "/rʌn/", "v. 跑；n. 跑步", "", "run definition", 1, 1),
                )
                conn.commit()
            finally:
                conn.close()

            target_words = root / "targets.txt"
            target_words.write_text("run\n", encoding="utf-8")
            target = root / "words.json"
            report_dir = root / "reports"

            summary = import_ecdict(source, target, target_words, report_dir)
            words = json.loads(target.read_text(encoding="utf-8"))

            self.assertEqual(summary["importedWords"], 1)
            self.assertEqual(words[0]["word"], "run")
            self.assertEqual([item["pos"] for item in words[0]["meanings"]], ["v.", "n."])

    def test_normalize_pos(self) -> None:
        self.assertEqual(normalize_pos("vt"), "vt.")
        self.assertEqual(normalize_pos("名词"), "n.")
        self.assertEqual(normalize_pos("adj："), "adj.")

    def test_builds_normalized_sqlite_with_multiple_wordbooks(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "ecdict.csv"
            source.write_text(
                "\n".join(
                    [
                        "word,phonetic,translation,pos,definition,bnc,frq",
                        "apply,/əˈplaɪ/,v. 申请；应用,v,apply definition,1,1",
                        "book,/bʊk/,n. 书；本子,n,book definition,2,2",
                    ]
                ),
                encoding="utf-8",
            )
            db = root / "ecdict.db"
            specs = [
                WordbookSpec(
                    id="zhongkao_core",
                    title="中考核心",
                    words=[
                        WordbookWordSpec(
                            word="apply",
                            stable_word_id="zk-apply",
                            unit="核心动词",
                            level="middle",
                            tags=["exam"],
                            priority=10,
                            sort_order=1,
                        )
                    ],
                ),
                WordbookSpec(
                    id="grade8_core",
                    title="八年级核心",
                    words=[
                        WordbookWordSpec(
                            word="apply",
                            stable_word_id="g8-apply",
                            unit="Unit 3",
                            level="grade8",
                            tags=["core"],
                            priority=5,
                            sort_order=1,
                        ),
                        WordbookWordSpec(
                            word="book",
                            stable_word_id="g8-book",
                            unit="Unit 1",
                            level="grade8",
                            tags=["core"],
                            priority=3,
                            sort_order=2,
                        ),
                    ],
                ),
            ]

            summary = build_ecdict_sqlite(source, db, specs)
            self.assertEqual(summary["entries"], 2)
            self.assertEqual(summary["wordbooks"], 2)
            self.assertEqual(summary["memberships"], 3)

            conn = sqlite3.connect(db)
            try:
                memberships = conn.execute(
                    "SELECT wordbook_id FROM wordbook_words WHERE word_key = ? ORDER BY wordbook_id",
                    ("apply",),
                ).fetchall()
                self.assertEqual([row[0] for row in memberships], ["grade8_core", "zhongkao_core"])
            finally:
                conn.close()

            target = root / "grade8_words.json"
            report_dir = root / "reports"
            export_summary = build_wordbook_json_from_sqlite(db, "grade8_core", target, report_dir)
            exported = json.loads(target.read_text(encoding="utf-8"))

            self.assertEqual(export_summary["exportedWords"], 2)
            self.assertEqual(exported[0]["id"], "g8-apply")
            self.assertEqual(exported[0]["book"], "八年级核心")
            self.assertEqual(exported[0]["unit"], "Unit 3")
            self.assertEqual(exported[0]["level"], "grade8")
            self.assertEqual(exported[0]["tags"], ["core"])
            self.assertEqual(exported[0]["meanings"][0]["pos"], "v.")


if __name__ == "__main__":
    unittest.main()
