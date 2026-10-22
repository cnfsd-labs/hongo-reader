#!/usr/bin/env python3
"""Exports the enabled term dictionaries of a Honmani database for LookupCoverageCheck.

Usage: export_terms.py <honmani.db> [<out.tsv>]

Copy the database from a debug build first, e.g.
  adb exec-out run-as app.honmani.reader.debug cat databases/honmani.db > honmani.db
(also -wal and -shm). The default output is app/build/lookup-coverage/terms.tsv, where the
check looks for it.
"""
import os
import sqlite3
import sys
import zlib


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
        os.path.dirname(__file__), "..", "..", "app", "build", "lookup-coverage", "terms.tsv")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    db = sqlite3.connect(sys.argv[1])
    presets = dict(db.execute("SELECT dictionary_id, preset FROM glossary_preset"))
    rows = db.execute(
        "SELECT t.id, t.dictionary_id, t.expression, t.reading, t.rules, t.score, t.sequence, t.glossary "
        "FROM term t JOIN dictionary d ON d.id = t.dictionary_id "
        "WHERE d.enabled = 1 AND d.status = 'READY'")
    count = 0
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        for *row, glossary in rows:
            # Only pointer entries (negative sequence) need their glossary: it names the target.
            row.append(unpack(glossary, presets.get(row[1])) if row[6] is not None and row[6] < 0 else "")
            f.write("\t".join("" if v is None else str(v).replace("\t", " ").replace("\n", " ") for v in row))
            f.write("\n")
            count += 1
    print(f"{count} terms -> {out}")


def unpack(glossary, preset):
    """The JSON of a stored glossary: a plain (0) or packed (1, raw deflate with a preset) one."""
    if glossary[0] == 0:
        return glossary[1:].decode("utf-8")
    inflater = zlib.decompressobj(wbits=-15, zdict=preset)
    return (inflater.decompress(glossary[1:]) + inflater.flush()).decode("utf-8")


if __name__ == "__main__":
    main()
