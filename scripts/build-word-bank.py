from __future__ import annotations

import argparse
import json
from pathlib import Path

from ecdict_pipeline import build_wordbook_json_from_sqlite


def main() -> int:
    parser = argparse.ArgumentParser(description="Build an app word bank JSON from normalized ecdict.db.")
    parser.add_argument("--db", required=True, help="Normalized ecdict.db path.")
    parser.add_argument("--wordbook-id", required=True, help="Wordbook id in wordbook_words.")
    parser.add_argument("--target", required=True, help="Output app wordbook JSON path.")
    parser.add_argument("--report-dir", default="docs/ecdict-reports", help="Report directory.")
    args = parser.parse_args()

    summary = build_wordbook_json_from_sqlite(
        db_path=Path(args.db),
        wordbook_id=args.wordbook_id,
        target=Path(args.target),
        report_dir=Path(args.report_dir),
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
