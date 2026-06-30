from __future__ import annotations

import argparse
import json
from pathlib import Path

from ecdict_pipeline import import_wordbook_memberships, load_wordbook_specs


def main() -> int:
    parser = argparse.ArgumentParser(description="Import wordbook membership specs into an existing ecdict.db.")
    parser.add_argument("--db", required=True, help="Existing ecdict.db path.")
    parser.add_argument("--wordbook", action="append", required=True, help="Wordbook spec JSON path. Can be passed multiple times.")
    args = parser.parse_args()

    summary = import_wordbook_memberships(Path(args.db), load_wordbook_specs([Path(path) for path in args.wordbook]))
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
