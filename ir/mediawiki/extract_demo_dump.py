#!/usr/bin/env python3
"""Extract an English Computer Science demo from an existing Markdown YaCy Pack."""

import argparse
import gzip
import json
import sys
import time
from pathlib import Path

from extract_wikipedia_dump_to_markdown import BASE_DIR, decompressed, dump_date, find_containers

SEARCH_TERM = 'Computer Science'
DEFAULT_DATE = '2026-10-01'


def extract(snapshot: str = DEFAULT_DATE, base: Path = BASE_DIR) -> tuple[int, int]:
    snapshot = dump_date(snapshot)
    base = Path(base)
    source = find_containers(base, 'en', snapshot)[0]
    target = base / '_markdown' / (
        f'YaCy-Pack_scroll-common-web_wikipedia-en-demo_{snapshot.replace("-", "")}.jsonl.gz')
    partial = target.with_name(target.name + '.part')
    for path in (target, partial):
        if path.exists():
            raise ValueError(f'Output already exists: {path}. Move it aside before rerunning.')
    needle = SEARCH_TERM.casefold()
    scanned = matched = 0
    started = time.monotonic()
    print(f'Extracting articles containing {SEARCH_TERM!r} from {source}\nWriting {partial}', flush=True)
    with partial.open('xb') as destination:
        with gzip.GzipFile(filename='', mode='wb', fileobj=destination, mtime=0) as output:
            with decompressed(source) as stream:
                for action_line in stream:
                    action_number = scanned * 2 + 1
                    try:
                        if action_line != b'{"index":{}}\n' and json.loads(action_line) != {'index': {}}:
                            raise ValueError('expected {"index":{}} action')
                    except ValueError as error:
                        raise ValueError(f'Invalid index action at {source}:{action_number}: {error}') from error
                    document_line = stream.readline()
                    if not document_line:
                        raise ValueError(f'Missing document after index action at {source}:{action_number}')
                    try:
                        document = json.loads(document_line)
                        if (not isinstance(document, dict)
                                or not isinstance(document.get('title'), list)
                                or len(document['title']) != 1
                                or not isinstance(document['title'][0], str)
                                or not document['title'][0]
                                or not isinstance(document.get('text_t'), str)
                                or not isinstance(document.get('url_s'), str)
                                or not document['url_s']
                                or document.get('language_s') != 'en'):
                            raise ValueError('expected an English article with url_s, one title, and text_t')
                    except (ValueError, TypeError) as error:
                        raise ValueError(f'Invalid document at {source}:{action_number + 1}: {error}') from error
                    scanned += 1
                    if needle in document['title'][0].casefold() or needle in document['text_t'].casefold():
                        # Preserve the original Bulk records, including all document metadata.
                        output.write(action_line)
                        output.write(document_line)
                        if not document_line.endswith(b'\n'):
                            output.write(b'\n')
                        matched += 1
                    if scanned % 100000 == 0:
                        print(f'  {scanned:,} articles scanned; {matched:,} selected '
                              f'({scanned / (time.monotonic() - started):.0f} articles/s)', flush=True)
    if target.exists():
        raise ValueError(f'Output appeared during extraction; refusing to replace {target}')
    partial.rename(target)
    print(f'Complete: {matched:,} of {scanned:,} articles selected in '
          f'{time.monotonic() - started:.1f}s\n{target}', flush=True)
    return scanned, matched


def main(argv=None):
    parser = argparse.ArgumentParser(
        description='Extract English articles containing the phrase "Computer Science" into a demo YaCy Pack.',
        epilog='Example: python3 extract_demo_dump.py\n'
               'Matches a case-insensitive substring in the title or Markdown text.\n'
               'Preserves Elasticsearch Bulk index/document pairs and all document fields.\n'
               'Reads _markdown/YaCy-Pack_*_wikipedia-en_<date>.jsonl.gz; writes\n'
               '_markdown/YaCy-Pack_scroll-common-web_wikipedia-en-demo_<date>.jsonl.gz.\n'
               'Existing outputs and unfinished .part files are not overwritten.',
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('date', nargs='?', type=dump_date, default=DEFAULT_DATE,
                        help=f'snapshot date, YYYY-MM-DD (default: {DEFAULT_DATE})')
    args = parser.parse_args(sys.argv[1:] if argv is None else argv)
    try:
        extract(args.date)
    except KeyboardInterrupt:
        print('Interrupted; output ending in .part is unfinished.', file=sys.stderr)
        return 130
    except (OSError, ValueError, RuntimeError, EOFError) as error:
        print(f'Error: {error}. Output ending in .part is unfinished.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
