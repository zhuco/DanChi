from __future__ import annotations

import argparse
import json
from pathlib import Path

from ecdict_pipeline import build_ecdict_sqlite, load_wordbook_specs, read_current_asset_as_wordbook_spec


def main() -> int:
    parser = argparse.ArgumentParser(description="Build normalized ecdict.db with wordbook membership tables.")
    parser.add_argument("--source", required=True, help="Local ECDICT CSV/SQLite source.")
    parser.add_argument("--target-db", required=True, help="Output SQLite database path.")
    parser.add_argument("--wordbook", action="append", default=[], help="Wordbook spec JSON path. Can be passed multiple times.")
    parser.add_argument("--current-wordbook-json", default="", help="Existing app wordbook JSON to preserve stable ids.")
    parser.add_argument("--current-wordbook-id", default="zhongkao", help="Wordbook id for --current-wordbook-json.")
    parser.add_argument("--current-wordbook-title", default="初中中考", help="Wordbook title for --current-wordbook-json.")
    parser.add_argument("--limit", type=int, default=0, help="Maximum entries when no wordbook target is provided.")
    args = parser.parse_args()

    specs = load_wordbook_specs([Path(path) for path in args.wordbook])
    if args.current_wordbook_json:
        specs.append(
            read_current_asset_as_wordbook_spec(
                Path(args.current_wordbook_json),
                wordbook_id=args.current_wordbook_id,
                title=args.current_wordbook_title,
            )
        )

    summary = build_ecdict_sqlite(
        source=Path(args.source),
        target_db=Path(args.target_db),
        wordbook_specs=specs,
        limit=args.limit,
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
