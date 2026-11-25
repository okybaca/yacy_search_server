#!/usr/bin/env python3
"""Audit retained document frequencies and save reproducible tables and figures."""

from __future__ import annotations

import argparse
import csv
import hashlib
import heapq
import html
import json
import math
import re
import sys
import unicodedata
from array import array
from collections import Counter
from dataclasses import asdict, dataclass, fields
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Optional, Sequence, Union

from extract_wikipedia_dump_to_markdown import BASE_DIR, dump_date, language_code

try:
    import numpy as np
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
except ImportError:
    np = None
    plt = None

CsvValue = Union[str, int, float, None]
MISSING_TERM_DF = 10
MIN_DF = (10, 20, 50, 100, 200, 500, 1000, 10000, 100000)
MAX_PREVALENCE = (1.0, 0.5, 0.2, 0.1, 0.05, 0.01)
PREVALENCE_EDGES = (0, 1e-6, 1e-5, 1e-4, 1e-3, 1e-2, 0.1, 1.0)
PREVALENCE_LABELS = ("<0.0001%", "0.0001–0.001%", "0.001–0.01%", "0.01–0.1%",
                     "0.1–1%", "1–10%", "10–100%")
# Literal tokens to inspect, not automatic stopwords or counts of redirect pages.
MARKERS = frozenset(("redirect", "weiterleitung", "転送", "重定向", "넘겨주기",
                     "เปลี่ยนทาง", "thumb", "nbsp", "displaystyle", "https", "www"))


@dataclass(frozen=True)
class CorpusSummary:
    language: str
    documents: int
    retained_terms: int
    retained_term_document_incidences: int
    retained_distinct_terms_per_document: float
    df_min: Optional[int]
    df_median: Optional[float]
    df_p90: Optional[float]
    df_p99: Optional[float]
    df_max: Optional[int]
    terms_at_df10: int
    fraction_at_df10: float
    fraction_at_df_le20: float
    top100_incidence_fraction: float
    idf_median: Optional[float]
    missing_df10_idf: Optional[float]
    missing_vs_rarest_idf_gap: Optional[float]
    single_codepoint_fraction: float
    long_ge30_codepoints_fraction: float
    non_nfc_fraction: float
    ascii_fraction: float
    redirect_token_df: int
    csv_bytes: int
    parquet_bytes: Optional[int]
    csv_to_parquet_size_ratio: Optional[float]
    source_csv: str
    source_sha256: str


def write_csv(path: Path, headers: Sequence[str], rows: Iterable[Sequence[CsvValue]]) -> None:
    with path.open("x", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter=";", lineterminator="\n")
        writer.writerow(headers)
        writer.writerows(rows)


def save_figure(figure, path: Path) -> None:
    figure.savefig(path.with_suffix(".png"), dpi=160, facecolor="white")
    figure.savefig(path.with_suffix(".svg"), facecolor="white")
    plt.close(figure)


def analyze(snapshot: str, input_dir: Path = BASE_DIR / "_statistics",
            output_dir: Optional[Path] = None,
            languages: Optional[Sequence[str]] = None) -> Path:
    """Read one CSV at a time; keep only compact plot data between languages."""
    snapshot = dump_date(snapshot)
    if np is None or plt is None:
        raise ValueError("Install analysis dependencies: python3 -m pip install -r requirements-analysis.txt")
    input_dir = Path(input_dir).resolve()
    output = Path(output_dir).resolve() if output_dir else input_dir / "_analysis" / snapshot
    partial = output.with_name(output.name + ".part")
    sources: dict[str, Path] = {}
    pattern = re.compile(r"([a-z][a-z0-9]*(?:_[a-z0-9]+)*)wiki-" + re.escape(snapshot)
                         + r"-document-frequency\.csv")
    for path in sorted(input_dir.glob(f"*wiki-{snapshot}-document-frequency.csv")):
        match = pattern.fullmatch(path.name)
        if match and path.is_file():
            sources[language_code(match[1])] = path
    if languages is not None:
        requested = {language_code(language) for language in languages}
        missing = requested - sources.keys()
        if missing:
            raise ValueError(f"Missing document-frequency CSVs for: {', '.join(sorted(missing))}")
        sources = {language: path for language, path in sources.items() if language in requested}
    if not sources:
        raise ValueError(f"No document-frequency CSVs for {snapshot} in {input_dir}")
    for path in (output, partial):
        if path.exists():
            raise ValueError(f"Output already exists: {path}. Choose another --output-dir.")
    partial.mkdir(parents=True)
    graphs = partial / "graphs"
    data = partial / "data"
    graphs.mkdir()
    data.mkdir()
    plt.rcParams.update({"font.size": 10, "axes.spines.top": False,
                         "axes.spines.right": False, "svg.fonttype": "none"})
    summaries: list[CorpusSummary] = []
    threshold_rows: list[tuple[CsvValue, ...]] = []
    top_rows: list[tuple[CsvValue, ...]] = []
    audit_rows: list[tuple[CsvValue, ...]] = []
    marker_rows: list[tuple[CsvValue, ...]] = []
    rank_rows: list[tuple[CsvValue, ...]] = []
    ccdf_rows: list[tuple[CsvValue, ...]] = []
    histogram_rows: list[tuple[CsvValue, ...]] = []
    length_rows: list[tuple[CsvValue, ...]] = []
    band_rows: list[tuple[CsvValue, ...]] = []
    band_shares: list[list[float]] = []
    threshold_shares: list[list[float]] = []

    for language, source in sources.items():
        print(f"Analyzing {source.name}", flush=True)
        initial_stat = source.stat()
        if source.with_suffix(".csv.part").exists():
            raise ValueError(f"Incomplete input: {source}")
        # Hash the original bytes for provenance, independently of CSV decoding.
        digest = hashlib.sha256()
        with source.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
        counts = array("q")
        seen: set[str] = set()
        lengths: Counter[int] = Counter()
        top: list[tuple[int, str]] = []
        longest: list[tuple[int, int, str]] = []
        markers: dict[str, int] = {}
        documents = None
        non_nfc = ascii_terms = 0
        with source.open(encoding="utf-8", newline="") as stream:
            reader = csv.reader(stream, delimiter=";")
            if next(reader, None) != ["term", "count"]:
                raise ValueError(f"Expected CSV header term;count: {source}")
            for line, row in enumerate(reader, 2):
                if len(row) != 2 or not row[0] or not re.fullmatch(r"[0-9]+", row[1]):
                    raise ValueError(f"Invalid term/count at {source}:{line}")
                term, raw_count = row
                count = int(raw_count)
                if term == "_":
                    if documents is not None:
                        raise ValueError(f"Duplicate document-count row at {source}:{line}")
                    documents = count
                    continue
                if term in seen:
                    raise ValueError(f"Duplicate term at {source}:{line}: {term!r}")
                if (not 0 < count <= np.iinfo(np.int64).max or term != term.lower()
                        or term.replace(".", "").replace(",", "").isdecimal()
                        or not ((count >= 10 and term >= "aa") or count > 100)):
                    raise ValueError(f"Term/count violates the export rules at {source}:{line}")
                seen.add(term)
                counts.append(count)
                length = len(term)
                lengths[length] += 1
                non_nfc += not unicodedata.is_normalized("NFC", term)
                ascii_terms += term.isascii()
                heapq.heappush(top, (count, term))
                if len(top) > 100:
                    heapq.heappop(top)
                heapq.heappush(longest, (length, count, term))
                if len(longest) > 50:
                    heapq.heappop(longest)
                if term in MARKERS:
                    markers[term] = count
        if documents is None:
            raise ValueError(f"Missing reserved _ document-count row: {source}")
        frequencies = np.sort(np.frombuffer(counts, dtype=np.int64))
        vocabulary = len(frequencies)
        if vocabulary and int(frequencies[-1]) > documents:
            raise ValueError(f"Document frequency exceeds the _ document count: {source}")
        if (source.stat().st_size, source.stat().st_mtime_ns) != (initial_stat.st_size, initial_stat.st_mtime_ns):
            raise ValueError(f"Input changed during analysis: {source}")
        del seen, counts
        incidences = sum(int(value) for value in frequencies)
        if incidences > np.iinfo(np.int64).max:
            raise ValueError(f"Term-document incidence total exceeds int64: {source}")
        cumulative = np.cumsum(frequencies, dtype=np.int64)
        quantiles = np.quantile(frequencies, [0.5, 0.9, 0.99]) if vocabulary else [None] * 3
        idf = np.log(documents / frequencies) if vocabulary else np.array([], dtype=float)
        fallback_idf = math.log(documents / MISSING_TERM_DF) if documents else None
        parquet = source.with_suffix(".parquet")
        parquet_bytes = parquet.stat().st_size if parquet.is_file() else None
        at10 = int(np.count_nonzero(frequencies == 10))
        summary = CorpusSummary(
            language=language, documents=documents, retained_terms=vocabulary,
            retained_term_document_incidences=incidences,
            retained_distinct_terms_per_document=incidences / documents if documents else 0,
            df_min=int(frequencies[0]) if vocabulary else None,
            df_median=float(quantiles[0]) if vocabulary else None,
            df_p90=float(quantiles[1]) if vocabulary else None,
            df_p99=float(quantiles[2]) if vocabulary else None,
            df_max=int(frequencies[-1]) if vocabulary else None,
            terms_at_df10=at10, fraction_at_df10=at10 / vocabulary if vocabulary else 0,
            fraction_at_df_le20=int(np.searchsorted(frequencies, 20, side="right")) / vocabulary if vocabulary else 0,
            top100_incidence_fraction=sum(count for count, _ in top) / incidences if incidences else 0,
            idf_median=float(np.median(idf)) if vocabulary else None,
            missing_df10_idf=fallback_idf,
            missing_vs_rarest_idf_gap=math.log(int(frequencies[0]) / MISSING_TERM_DF) if vocabulary else None,
            single_codepoint_fraction=lengths[1] / vocabulary if vocabulary else 0,
            long_ge30_codepoints_fraction=sum(count for length, count in lengths.items() if length >= 30) / vocabulary if vocabulary else 0,
            non_nfc_fraction=non_nfc / vocabulary if vocabulary else 0,
            ascii_fraction=ascii_terms / vocabulary if vocabulary else 0,
            redirect_token_df=markers.get("redirect", 0),
            csv_bytes=initial_stat.st_size, parquet_bytes=parquet_bytes,
            csv_to_parquet_size_ratio=initial_stat.st_size / parquet_bytes if parquet_bytes else None,
            source_csv=str(source), source_sha256=digest.hexdigest())
        summaries.append(summary)

        retained_at_threshold = []
        for kind, thresholds in (("min_df", MIN_DF), ("max_prevalence", MAX_PREVALENCE)):
            for threshold in thresholds:
                if kind == "min_df":
                    boundary = int(np.searchsorted(frequencies, threshold, side="left"))
                    retained = vocabulary - boundary
                    mass = incidences - (int(cumulative[boundary - 1]) if boundary else 0)
                    retained_at_threshold.append(retained / vocabulary if vocabulary else 0)
                else:
                    boundary = int(np.searchsorted(frequencies, threshold * documents, side="right"))
                    retained = boundary
                    mass = int(cumulative[boundary - 1]) if boundary else 0
                threshold_rows.append((language, kind, threshold, retained,
                                       retained / vocabulary if vocabulary else 0,
                                       mass, mass / incidences if incidences else 0))
        threshold_shares.append(retained_at_threshold)
        for rank, (count, term) in enumerate(sorted(top, key=lambda item: (-item[0], item[1])), 1):
            top_rows.append((language, rank, term, count, count / documents, math.log(documents / count)))
        for length, count, term in sorted(longest, reverse=True):
            audit_rows.append((language, term, length, count, count / documents))
        for term, count in sorted(markers.items()):
            marker_rows.append((language, term, count, count / documents))
        for length, count in sorted(lengths.items()):
            length_rows.append((language, length, count, count / vocabulary))
        prevalence = frequencies / documents if vocabulary else np.array([], dtype=float)
        bands, _ = np.histogram(prevalence, bins=PREVALENCE_EDGES)
        shares = [int(count) / vocabulary if vocabulary else 0 for count in bands]
        band_shares.append(shares)
        for label, count, fraction in zip(PREVALENCE_LABELS, bands, shares):
            band_rows.append((language, label, int(count), fraction))

        figure, axes = plt.subplots(2, 2, figsize=(12, 8), layout="constrained")
        figure.suptitle(f"Wikipedia {language} · {snapshot} · {documents:,} documents · {vocabulary:,} retained terms")
        if vocabulary:
            ranks = np.unique(np.geomspace(1, vocabulary, min(800, vocabulary)).astype(np.int64))
            ranked_df = frequencies[-ranks]
            for rank, count in zip(ranks, ranked_df):
                rank_rows.append((language, int(rank), int(count), int(count) / documents))
            axes[0, 0].loglog(ranks, ranked_df / documents, color="#176b87")
            axes[0, 0].set(xlabel="Term rank (descending DF)", ylabel="Document fraction: DF / N",
                           title="Rank versus document prevalence")
            grid = np.unique(np.ceil(np.geomspace(frequencies[0], frequencies[-1], 160)).astype(np.int64))
            ccdf = (vocabulary - np.searchsorted(frequencies, grid, side="left")) / vocabulary
            for threshold, fraction in zip(grid, ccdf):
                ccdf_rows.append((language, int(threshold), float(fraction)))
            axes[0, 1].loglog(grid, ccdf, color="#9c4f30")
            axes[0, 1].set(xlabel="Minimum DF", ylabel="Fraction of retained terms with DF ≥ x",
                           title="Survival curve of retained vocabulary")
            idf_counts, idf_edges = np.histogram(idf, bins=40, range=(0, max(1, math.log(max(documents, 1)))))
            axes[1, 0].stairs(idf_counts / vocabulary, idf_edges, fill=True, color="#176b87", alpha=0.8)
            axes[1, 0].axvline(fallback_idf, color="#b7503c", linestyle="--",
                               label=f"Missing term: DF = {MISSING_TERM_DF}")
            axes[1, 0].legend(fontsize=8)
            axes[1, 0].set(xlabel="IDF = ln(N / DF)", ylabel="Fraction of retained terms per bin",
                           title="Observed IDF and missing-term fallback")
            for left, right, count in zip(idf_edges[:-1], idf_edges[1:], idf_counts):
                histogram_rows.append((language, float(left), float(right), int(count), int(count) / vocabulary))
            axes[1, 1].bar(list(lengths), [count / vocabulary for count in lengths.values()], color="#388579")
            axes[1, 1].set(xscale="log", yscale="log", xlabel="Term length (Unicode code points)",
                           ylabel="Fraction of retained terms", title="Token length diagnostic")
        else:
            for axis in axes.flat:
                axis.text(0.5, 0.5, "No retained terms", ha="center", va="center", transform=axis.transAxes)
                axis.set_axis_off()
        for axis in axes.flat:
            axis.grid(alpha=0.15)
        save_figure(figure, graphs / f"{language}-diagnostics")
        print(f"  {vocabulary:,} terms; validation passed; figures saved", flush=True)

    summary_fields = [field.name for field in fields(CorpusSummary)]
    write_csv(data / "summary.csv", summary_fields,
              ([asdict(summary)[name] for name in summary_fields] for summary in summaries))
    write_csv(data / "threshold_sensitivity.csv",
              ("language", "rule", "threshold", "terms_remaining", "term_fraction_remaining",
               "incidences_remaining", "incidence_fraction_remaining"), threshold_rows)
    write_csv(data / "top_terms.csv", ("language", "rank", "term", "df", "document_fraction", "idf_ln"), top_rows)
    write_csv(data / "longest_terms.csv", ("language", "term", "codepoints", "df", "document_fraction"), audit_rows)
    write_csv(data / "marker_terms.csv", ("language", "literal_token", "df", "document_fraction"), marker_rows)
    write_csv(data / "rank_frequency.csv", ("language", "rank", "df", "document_fraction"), rank_rows)
    write_csv(data / "df_survival.csv", ("language", "min_df", "fraction_of_terms_ge_df"), ccdf_rows)
    write_csv(data / "idf_histogram.csv", ("language", "left_inclusive", "right_exclusive_except_last", "terms", "term_fraction"), histogram_rows)
    write_csv(data / "term_lengths.csv", ("language", "codepoints", "terms", "term_fraction"), length_rows)
    write_csv(data / "prevalence_bands.csv", ("language", "prevalence_band", "terms", "term_fraction"), band_rows)

    labels = [summary.language for summary in summaries]
    y = np.arange(len(summaries))
    height = max(4, len(summaries) * 0.3 + 1.5)
    figure, axes = plt.subplots(1, 2, figsize=(12, height), layout="constrained")
    for axis, values, title in (
            (axes[0], [summary.documents for summary in summaries], "Documents (includes redirects and empty articles)"),
            (axes[1], [summary.retained_terms for summary in summaries], "Retained vocabulary (filtered, not full vocabulary)")):
        axis.barh(y, values, color="#176b87")
        axis.set_yticks(y, labels)
        axis.invert_yaxis()
        if any(values):
            axis.set_xscale("log")
        axis.set(title=title, xlabel="Count (log scale when nonzero)")
        axis.grid(axis="x", alpha=0.15)
    save_figure(figure, graphs / "corpus-overview")

    figure, axis = plt.subplots(figsize=(12, height), layout="constrained")
    image = axis.imshow(np.array(band_shares) * 100, aspect="auto", vmin=0, vmax=100, cmap="Blues")
    axis.set_yticks(y, labels)
    axis.set_xticks(np.arange(len(PREVALENCE_LABELS)), PREVALENCE_LABELS, rotation=25, ha="right")
    axis.set(title="Share of retained vocabulary by document prevalence", xlabel="DF / N band")
    figure.colorbar(image, ax=axis, label="Percent of retained terms")
    save_figure(figure, graphs / "prevalence-bands")

    figure, axis = plt.subplots(figsize=(12, height), layout="constrained")
    values = np.array(threshold_shares) * 100
    image = axis.imshow(values, aspect="auto", vmin=0, vmax=100, cmap="YlGnBu")
    for row in range(len(labels)):
        for column in range(len(MIN_DF)):
            axis.text(column, row, f"{values[row, column]:.0f}", ha="center", va="center",
                      fontsize=8, color="white" if values[row, column] > 65 else "#172c3c")
    axis.set_yticks(y, labels)
    axis.set_xticks(np.arange(len(MIN_DF)), [f"{value:,}" for value in MIN_DF])
    axis.set(title="Vocabulary remaining after increasing minimum DF", xlabel="Keep DF ≥ threshold (applied to already retained terms)")
    figure.colorbar(image, ax=axis, label="Percent of retained vocabulary remaining")
    save_figure(figure, graphs / "threshold-sensitivity")

    figure, axes = plt.subplots(1, 3, figsize=(14, height), layout="constrained")
    for axis, attribute, title in (
            (axes[0], "single_codepoint_fraction", "One code point"),
            (axes[1], "long_ge30_codepoints_fraction", "At least 30 code points"),
            (axes[2], "non_nfc_fraction", "Not normalized to NFC")):
        axis.barh(y, [getattr(summary, attribute) * 100 for summary in summaries], color="#388579")
        axis.set_yticks(y, labels)
        axis.invert_yaxis()
        axis.set(title=title, xlabel="Percent of retained terms")
        axis.grid(axis="x", alpha=0.15)
    figure.suptitle("Tokenizer diagnostics · descriptive signals, not automatic errors")
    save_figure(figure, graphs / "token-diagnostics")

    # Formula comparison at matched prevalence; corpus N still affects smoothing.
    figure, axes = plt.subplots(1, 2, figsize=(12, 5), layout="constrained")
    weight_rows: list[tuple[CsvValue, ...]] = []
    for summary in summaries:
        if not summary.documents:
            continue
        df_grid = np.unique(np.ceil(np.geomspace(1, summary.documents, 100)).astype(np.int64))
        standard = np.log(summary.documents / df_grid)
        bm25 = np.log1p((summary.documents - df_grid + 0.5) / (df_grid + 0.5))
        for df, classic, positive in zip(df_grid, standard, bm25):
            weight_rows.append((summary.language, int(df), int(df) / summary.documents, float(classic), float(positive)))
        axes[0].plot(df_grid / summary.documents, standard, alpha=0.45, linewidth=1)
        axes[1].plot(df_grid / summary.documents, bm25 - standard, alpha=0.45, linewidth=1)
    axes[0].set(title="Classic IDF across corpus sizes", ylabel="ln(N / DF)")
    axes[1].set(title="Positive BM25 IDF minus classic IDF", ylabel="Difference in natural-log units")
    for axis in axes:
        axis.set(xscale="log", xlabel="DF / N (each line is one language)")
        axis.grid(alpha=0.15)
    save_figure(figure, graphs / "idf-formulas")
    write_csv(data / "idf_formulas.csv", ("language", "df", "document_fraction", "classic_idf_ln", "positive_bm25_idf_ln"), weight_rows)

    manifest = {
        "snapshot": snapshot,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "program_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "python": sys.version, "numpy": np.__version__, "matplotlib": matplotlib.__version__,
        "validation": "Passed: schema, unique terms, one corpus count, lowercase terms, numeric exclusion, export filter, 0 < DF <= N, stable input files.",
        "population": "Retained vocabulary only; the _ row is corpus N and is excluded from vocabulary statistics.",
        "filter": "(DF >= 10 and term >= 'aa') or DF > 100; decimal-only terms excluded.",
        "idf": f"Natural logarithms; classic ln(N/DF); positive BM25 ln(1+(N-DF+0.5)/(DF+0.5)); missing-term scenario uses DF={MISSING_TERM_DF}.",
        "quantiles": "NumPy default linear interpolation, unweighted across retained terms.",
        "rank_plot": "Up to 800 geometrically spaced exact ranks per language; no power-law fit.",
        "top_terms": "Top 100 by DF; ties at the cutoff prefer lexicographically larger terms. Displayed ties sort alphabetically.",
        "length": "Python string length in Unicode code points, not graphemes or linguistic words.",
        "parquet": "File sizes only; CSV is the analysis source. Parquet contents were not compared.",
        "corpora": [asdict(summary) for summary in summaries],
    }
    with (partial / "summary.json").open("x", encoding="utf-8") as stream:
        json.dump(manifest, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")

    nonempty = [summary for summary in summaries if summary.retained_terms]
    total_documents = sum(summary.documents for summary in summaries)
    total_terms = sum(summary.retained_terms for summary in summaries)
    parts = [f"""<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Wikipedia document-frequency analysis · {snapshot}</title>
<style>body{{font:16px/1.6 system-ui,sans-serif;color:#173042;background:#f5f7f8;margin:0}}
main{{max-width:1200px;margin:auto;padding:32px}}h1,h2,h3{{line-height:1.2}}h1{{font-size:36px}}
section{{background:white;border:1px solid #dce4e8;border-radius:10px;padding:24px;margin:24px 0}}
img{{max-width:100%;height:auto}}table{{border-collapse:collapse;font-size:14px;width:100%}}
th,td{{text-align:right;padding:8px;border-bottom:1px solid #dce4e8}}th:first-child,td:first-child{{text-align:left}}
.scroll{{overflow:auto}}a{{color:#176b87}}code{{background:#edf2f4;padding:2px 4px}}.note{{color:#526573}}
</style><main><h1>Wikipedia document-frequency analysis</h1>
<p>{snapshot} · {len(summaries)} languages · {total_documents:,} documents · {total_terms:,} retained language–term pairs</p>
<p class="note">Descriptive analysis of a filtered Wikipedia census. This report does not measure retrieval quality.</p>
<section><h2>Data checks and scope</h2>
<p>All input files passed schema, uniqueness, corpus-size, numeric-exclusion and export-filter checks.
The reserved <code>_</code> row supplies N; it is never treated as a term. Source SHA-256 hashes,
file sizes, software versions and metric definitions are saved in <a href="summary.json">summary.json</a>.</p>
<p>The source retains terms satisfying <code>(DF ≥ 10 and term ≥ 'aa') or DF &gt; 100</code>,
and removes decimal-only terms. Missing terms can be rare, filtered, unseen or tokenizer mismatches.
Their number and true DF cannot be reconstructed from these CSVs.</p>
<p>N includes redirects and empty articles. DF sums count term–document incidences, not word occurrences;
incidence fractions are not document coverage. Every percentage of vocabulary below refers only to retained terms.</p></section>"""]
    if nonempty:
        smallest = min(nonempty, key=lambda summary: summary.documents)
        largest = max(nonempty, key=lambda summary: summary.documents)
        boundary = max(nonempty, key=lambda summary: summary.fraction_at_df10)
        long_tokens = max(nonempty, key=lambda summary: summary.long_ge30_codepoints_fraction)
        redirect = max(nonempty, key=lambda summary: summary.redirect_token_df / summary.documents)
        parts.append(f"""<section><h2>Findings to investigate</h2><ul>
<li>Corpus size ranges from {smallest.documents:,} documents ({smallest.language}) to {largest.documents:,}
({largest.language}). The absolute DF=10 boundary corresponds to document prevalence
{10 / largest.documents:.6%}–{10 / smallest.documents:.6%}; small and large editions therefore have different censoring.</li>
<li>{boundary.language} has the largest share of vocabulary exactly at DF=10: {boundary.fraction_at_df10:.1%}.
Threshold sensitivity quantifies what is lost if that boundary is raised; it cannot estimate terms already removed.</li>
<li>{long_tokens.language} has the largest share of terms at least 30 code points long:
{long_tokens.long_ge30_codepoints_fraction:.1%}. Review its longest-term samples and segmentation before interpreting vocabulary size.</li>
<li>The literal token <code>redirect</code> occurs in {redirect.redirect_token_df:,} documents
({redirect.redirect_token_df / redirect.documents:.1%}) in {redirect.language}.
This is a token-presence diagnostic, not a count of redirect pages. Review extraction and redirect handling.</li>
<li>Missing terms use DF={MISSING_TERM_DF}, assigning ln(N/{MISSING_TERM_DF}) weight,
the same as retained terms at the long-tail cutoff. This is a cutoff-based fallback policy;
missing terms' true document frequencies remain unknown.</li>
</ul></section>""")
    parts.append("<section><h2>Corpus comparison</h2><div class='scroll'><table><tr><th>Language</th><th>Documents</th><th>Retained terms</th><th>Median DF</th><th>Mean retained distinct terms/document</th><th>DF=10 share</th><th>CSV/Parquet size</th></tr>")
    for summary in summaries:
        median = f"{summary.df_median:,.1f}" if summary.df_median is not None else "—"
        ratio = f"{summary.csv_to_parquet_size_ratio:.2f}×" if summary.csv_to_parquet_size_ratio is not None else "—"
        parts.append(f"<tr><td><a href='#language-{summary.language}'>{summary.language}</a></td><td>{summary.documents:,}</td><td>{summary.retained_terms:,}</td><td>{median}</td><td>{summary.retained_distinct_terms_per_document:,.1f}</td><td>{summary.fraction_at_df10:.1%}</td><td>{ratio}</td></tr>")
    parts.append("</table></div></section>")
    for slug, title, explanation in (
            ("corpus-overview", "Corpus and vocabulary sizes", "Vocabulary size is affected by morphology, tokenizer behavior and censoring. It is not a quality score."),
            ("prevalence-bands", "Normalized document prevalence", "Bands compare DF/N across languages. Different absolute cutoffs still censor different parts of each distribution; bands are left-inclusive and right-exclusive, except the final band includes 1."),
            ("threshold-sensitivity", "Pruning sensitivity", "Cells show the percent of retained vocabulary left after raising minimum DF. The CSV also records retained incidence mass and experiments removing very common terms."),
            ("token-diagnostics", "Tokenizer and normalization diagnostics", "Long and single-code-point tokens can be valid. Non-NFC forms warrant an analyzer-compatibility check; the analysis does not normalize or merge them."),
            ("idf-formulas", "Candidate IDF weights", "Both curves are weighting scenarios, not measured ranking gains. Classic IDF is ln(N/DF); positive BM25 IDF is ln(1+(N−DF+0.5)/(DF+0.5)). N here is the export's corpus count, which can differ from Lucene's field-specific document count.")):
        parts.append(f"<section><h2>{title}</h2><p>{explanation}</p><a href='graphs/{slug}.svg'>SVG</a><br><img loading='lazy' src='graphs/{slug}.png' alt='{title}'></section>")
    parts.append(f"""<section><h2>Next evaluation steps</h2><ol>
<li><strong>Validate the text representation.</strong> Inspect top terms, literal markers and long-token samples;
audit redirect and empty-article handling in the original packs. Compare the exact indexing and query analyzers
with this Unicode tokenizer, especially for Chinese, Japanese, Korean and Thai. Repeat extraction/counting only
after a concrete issue is confirmed.</li>
<li><strong>Measure query coverage.</strong> Collect a held-out multilingual query set, retaining query language
and tokenizer version. Measure missing-token rate, frequency of DF={MISSING_TERM_DF} fallback, and missing terms by query type.
The current CSVs cannot supply those measurements.</li>
<li><strong>Run controlled ranking experiments.</strong> Compare the current ranking baseline with classic IDF,
positive BM25 IDF, and a capped or smoothed missing-term fallback. Keep document scoring and all other parameters
fixed; the IDF factor alone is not a full BM25 implementation. Evaluate per-language nDCG@10, MRR and Recall@100
against relevance judgments. Compare per-query deltas with paired bootstrap confidence intervals and report latency.
Tune on development queries and report final results on untouched test queries.</li>
<li><strong>Test transfer to web search.</strong> Separate Wikipedia-style queries from web, navigational,
recent-event and rare-entity queries. Compare Wikipedia DF with local web-index DF and a blended prior;
do not infer relevance gains from these frequency plots alone.</li>
</ol><p>DF alone does not expose total vocabulary before filtering, term co-occurrence, document-length
distributions, redirect-page counts, query coverage, or relevance. No power-law significance test,
cross-language quality ranking or invented sampling uncertainty is reported.</p>
<p>Method references: <a href="https://nlp.stanford.edu/IR-book/html/htmledition/inverse-document-frequency-1.html">Stanford: inverse document frequency</a>;
<a href="https://lucene.apache.org/core/9_12_1/core/org/apache/lucene/search/similarities/BM25Similarity.html">Lucene: positive BM25 IDF and corpus count</a>;
<a href="https://github.com/usnistgov/trec_eval">NIST trec_eval</a>.</p></section>""")
    for summary in summaries:
        terms = [row for row in top_rows if row[0] == summary.language][:10]
        parts.append(f"<section id='language-{summary.language}'><h2>{summary.language}: distribution diagnostics</h2><p>Top retained tokens: ")
        parts.append(", ".join(f"<code>{html.escape(str(row[2]))}</code> ({row[4]:.1%})" for row in terms) or "none")
        parts.append(f".</p><a href='graphs/{summary.language}-diagnostics.svg'>SVG</a><br><img loading='lazy' src='graphs/{summary.language}-diagnostics.png' alt='{summary.language} frequency, IDF and length distributions'></section>")
    parts.append("<section><h2>Reusable data</h2><ul>")
    for path in sorted(data.glob("*.csv")):
        parts.append(f"<li><a href='data/{path.name}'>{path.name}</a></li>")
    parts.append("</ul><p>All tables are UTF-8, semicolon-delimited CSV with headers. Graphs are saved as PNG and SVG. Rank curves are sampled for plotting; scalar summaries and threshold counts use all retained terms.</p></section></main></html>")
    (partial / "report.html").write_text("\n".join(parts), encoding="utf-8")
    if output.exists():
        raise ValueError(f"Output appeared during analysis; refusing to replace {output}")
    partial.rename(output)
    print(f"Complete: {len(summaries)} languages\n{output / 'report.html'}", flush=True)
    return output


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Analyze document-frequency CSVs and save tables, PNG/SVG graphs and an HTML report.",
        epilog="Example: python3 analyze_document_frequency.py 2026-10-01\n"
               "Reads one language at a time; never reads the term-frequency JSONL files.\n"
               "Default output: _statistics/_analysis/<date>/. Existing output directories are refused.",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("date", type=dump_date)
    parser.add_argument("--input-dir", type=Path, default=BASE_DIR / "_statistics")
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--languages", nargs="+", type=language_code, default=None,
                        help="analyze only these languages (default: all matching CSVs)")
    args = sys.argv[1:] if argv is None else argv
    if not args:
        parser.print_help()
        return 0
    args = parser.parse_args(args)
    try:
        analyze(args.date, args.input_dir, args.output_dir, args.languages)
    except KeyboardInterrupt:
        print("Interrupted; analysis directories ending in .part are unfinished.", file=sys.stderr)
        return 130
    except (OSError, ValueError, RuntimeError, csv.Error) as error:
        print(f"Error: {error}. Analysis directories ending in .part are unfinished.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
