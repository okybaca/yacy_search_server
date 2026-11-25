#!/usr/bin/env python3
"""Run each Wikipedia processing stage for all release languages."""

import argparse
import subprocess
import sys

from extract_wikipedia_dump_to_markdown import BASE_DIR, PACK_TIERS, dump_date, pack_path

languages: list[str] = [
    "ar", "cs", "da", "de", "el", "en", "es", "fa", "fi", "fr",
    "he", "hi", "hu", "id", "it", "ja", "ko", "ku", "nl", "no",
    "pl", "pt", "ru", "sr", "sv", "th", "tr", "uk", "vi", "zh"
]


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Download all release languages, then extract Markdown, count terms, "
                    "count document frequencies, and compress the CSV files as Parquet.",
        epilog="Example: python3 release.py 2026-09-01\n"
               "The date must match the latest completed dumps selected by the downloader.\n"
               "Existing Markdown packs skip download and extraction.\n"
               "Completed term-frequency, document-frequency, and Parquet files are skipped.",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("date", type=dump_date)
    args = sys.argv[1:] if argv is None else argv
    if not args:
        parser.print_help()
        return 0
    args = parser.parse_args(args)

    try:
        languages_to_extract: list[str] = []
        for language in languages:
            if any(pack_path(BASE_DIR / "_markdown", language, args.date, tier).is_file()
                   for tier in PACK_TIERS):
                print(f"Markdown already exists for {language}/{args.date}; "
                      "skipping download and extraction.", flush=True)
            else:
                languages_to_extract.append(language)

        print("Downloading dumps", flush=True)
        for language in languages_to_extract:
            subprocess.run([str(BASE_DIR / "download_wikipedia_dump.sh"), language],
                           cwd=BASE_DIR, check=True)

        print("Extracting Markdown", flush=True)
        for language in languages_to_extract:
            subprocess.run([sys.executable, "extract_wikipedia_dump_to_markdown.py",
                            language, args.date], cwd=BASE_DIR, check=True)

        for script, suffix in (
                ("markdown_to_term_frequency.py", "term-frequency.jsonl"),
                ("term_frequency_to_document_frequency.py", "document-frequency.csv"),
                ("write_parquet.py", "document-frequency.parquet")):
            print(f"Running {script}", flush=True)
            for language in languages:
                prefix = f"{language.replace('-', '_')}wiki-{args.date}"
                target = BASE_DIR / "_statistics" / f"{prefix}-{suffix}"
                if target.is_file():
                    print(f"Already complete; skipping {target.name}", flush=True)
                    continue
                command = [sys.executable, script, language, args.date]
                if script == "write_parquet.py":
                    command.extend(["--compression-level", "22"])
                subprocess.run(command, cwd=BASE_DIR, check=True)
    except KeyboardInterrupt:
        print("Interrupted; outputs ending in .part are unfinished.", file=sys.stderr)
        return 130
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
