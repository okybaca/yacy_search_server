#!/usr/bin/env python3
"""Sum exact document-frequency terms across all release-language Parquet files."""

from __future__ import annotations

import argparse
import heapq
import sys
import time
from itertools import groupby
from operator import itemgetter
from pathlib import Path
from typing import Iterator, Optional

from extract_wikipedia_dump_to_markdown import BASE_DIR, dump_date, snapshot_prefix
from release import languages

try:
    import pyarrow as pa
    import pyarrow.parquet as pq
except ImportError:
    pa = None

INPUT_BATCH_SIZE = 16384
OUTPUT_ROW_GROUP_SIZE = 262144
MAX_COUNT = (1 << 63) - 1


def term_counts(source: Path) -> Iterator[tuple[str, int]]:
    """Keep one decoded batch per input open while the sorted merge consumes it."""
    with pq.ParquetFile(source) as reader:
        schema = reader.schema_arrow
        if (schema.names != ['term', 'count'] or schema.field('term').type != pa.string()
                or schema.field('count').type != pa.int64()):
            raise ValueError(f'Expected term: string and count: int64 columns: {source}')
        previous = None
        documents = None
        maximum_df = 0
        for batch in reader.iter_batches(batch_size=INPUT_BATCH_SIZE, use_threads=False):
            terms = batch.column(0).to_pylist()
            counts = batch.column(1).to_pylist()
            for term, count in zip(terms, counts):
                if not term or count is None or count < 0 or (term != '_' and count == 0):
                    raise ValueError(f'Invalid term/count in {source}: {term!r}, {count!r}')
                if previous is not None and term <= previous:
                    raise ValueError(f'Terms must be unique and sorted in {source}: {term!r}')
                previous = term
                if term == '_':
                    documents = count
                else:
                    maximum_df = max(maximum_df, count)
                yield term, count
        if documents is None:
            raise ValueError(f'Missing reserved _ document-count row: {source}')
        if maximum_df > documents:
            raise ValueError(f'Document frequency exceeds the _ document count: {source}')


def combine(snapshot: str, input_dir: Path = BASE_DIR / '_statistics',
            output: Optional[Path] = None) -> Path:
    snapshot = dump_date(snapshot)
    if pa is None:
        raise ValueError('Install dependencies with: python3 -m pip install -r requirements.txt')
    input_dir = Path(input_dir).resolve()
    sources = [input_dir / f'{snapshot_prefix(language, snapshot)}-document-frequency.parquet'
               for language in languages]
    if not sources or len(set(sources)) != len(sources):
        raise ValueError('Release languages must be nonempty and unique')
    for source in sources:
        if source.with_suffix('.parquet.part').exists():
            raise ValueError(f'Incomplete input: {source}')
        if not source.is_file():
            raise ValueError(f'Missing release-language input: {source}')
    target = Path(output).resolve() if output else (
        input_dir / f'wikipedia-combined-{snapshot}-document-frequency.parquet')
    partial = target.with_suffix(target.suffix + '.part')
    for path in (target, partial):
        if path.exists():
            raise ValueError(f'Output already exists: {path}. Choose another --output.')
    target.parent.mkdir(parents=True, exist_ok=True)
    schema = pa.schema([('term', pa.string()), ('count', pa.int64())], metadata={
        b'snapshot': snapshot.encode('ascii'),
        b'languages': ','.join(languages).encode('ascii'),
        b'aggregation': b'unweighted sum of exact stored terms; absent terms contribute zero',
    })
    inputs = [term_counts(source) for source in sources]
    terms: list[str] = []
    counts: list[int] = []
    rows = documents = 0
    started = time.monotonic()
    print(f'Merging {len(sources)} release languages into {target}', flush=True)
    try:
        # Reserve the output exclusively; publish only after every input is exhausted and validated.
        with partial.open('xb') as stream:
            with pq.ParquetWriter(
                    stream, schema, compression='zstd', compression_level=22,
                    use_dictionary=False,
                    column_encoding={'term': 'DELTA_BYTE_ARRAY', 'count': 'DELTA_BINARY_PACKED'},
                    sorting_columns=pq.SortingColumn.from_ordering(schema, [('term', 'ascending')])) as writer:
                term_key = itemgetter(0)
                merged = heapq.merge(*inputs, key=term_key)
                for term, contributions in groupby(merged, key=term_key):
                    count = sum(value for _, value in contributions)
                    if count > MAX_COUNT:
                        raise OverflowError(f'Combined count exceeds int64 for term {term!r}')
                    terms.append(term)
                    counts.append(count)
                    rows += 1
                    if term == '_':
                        documents = count
                    if len(terms) == OUTPUT_ROW_GROUP_SIZE:
                        writer.write_table(pa.Table.from_pydict({'term': terms, 'count': counts}, schema=schema),
                                           row_group_size=OUTPUT_ROW_GROUP_SIZE)
                        terms.clear()
                        counts.clear()
                        print(f'  {rows:,} combined rows written', flush=True)
                if terms:
                    writer.write_table(pa.Table.from_pydict({'term': terms, 'count': counts}, schema=schema),
                                       row_group_size=OUTPUT_ROW_GROUP_SIZE)
    finally:
        for records in inputs:
            records.close()
    if target.exists():
        raise ValueError(f'Output appeared during the merge; refusing to replace {target}')
    partial.rename(target)
    print(f'Complete: {rows - 1:,} terms, {documents:,} documents, '
          f'{len(sources)} languages in {time.monotonic() - started:.1f}s\n{target}', flush=True)
    return target


def main(argv=None):
    parser = argparse.ArgumentParser(
        description='Sum document-frequency counts across all release-language Parquet files.',
        epilog='Example: python3 combine_document_frequency.py 2026-10-01\n'
               'Requires every language in release.py. Sums exact terms and the reserved _ corpus count.\n'
               'Writes _statistics/wikipedia-combined-<date>-document-frequency.parquet, sorted by term.\n'
               'Uses ZSTD level 22 and bounded batches. Existing outputs are not overwritten.',
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('date', type=dump_date)
    parser.add_argument('--input-dir', type=Path, default=BASE_DIR / '_statistics')
    parser.add_argument('--output', type=Path, default=None, help='custom output Parquet path')
    args = sys.argv[1:] if argv is None else argv
    if not args:
        parser.print_help()
        return 0
    args = parser.parse_args(args)
    try:
        combine(args.date, args.input_dir, args.output)
    except KeyboardInterrupt:
        print('Interrupted; output ending in .part is unfinished.', file=sys.stderr)
        return 130
    except (OSError, ValueError, RuntimeError, OverflowError, MemoryError) as error:
        print(f'Error: {error}. Output ending in .part is unfinished.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
