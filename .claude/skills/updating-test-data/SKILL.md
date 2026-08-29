---
name: updating-test-data
description: How to regenerate verified data and change render snapshots for QR Code Press. Use when a verified data test or a render snapshot (SVG, PNG, rectangles) needs updating, or before editing anything under src/test/resources/.
---

# Updating test data

The test resources come in three kinds, and the top-level directory says which:

| Directory | Written by | Edit by hand? |
|---|---|---|
| `src/test/resources/input/` | nobody, hand-written | **yes**, this is the source of truth |
| `src/test/resources/verified/` | `VerifiedDataExport` | **never**, regenerate instead |
| `src/test/resources/render/` | nobody, no generator exists | **yes**, deliberately |

**Verified data** is written by `VerifiedDataExport` in `qr-code-press/src/test/java/`. It is a frozen
baseline. A regeneration is expected to change nothing, and `VerifiedDataExportTest` asserts exactly
that on every `./mvnw verify`, by exporting into a temporary directory and comparing recursively
against the committed files. The comparison covers the whole of `verified/` with nothing
subtracted, so a file there that the export does not produce is a finding, not something to add to
an exception list.

Regenerate from the `qr-code-press` directory with:

```sh
./mvnw test -Dtest=VerifiedDataExportTest -Dverified.export.write=true
```

It prints the files it changed, or `no files changed`. Regeneration and verification are the same
code path, so the exporter cannot drift from the data it produces.

**A diff after regenerating is a change in the library's behaviour, not a stale fixture.** Find out
what changed and why before keeping it. The exporter runs the library under test, so it can no
longer disagree with anything external. The independent checks are `ZxingRoundTripTest` and
`ReedSolomonTest`.

The **inputs** under `input/` are:

- `texts.tsv` has one row per payload text: its index, the file under `input/texts/` holding it,
  and the ECI it is encoded with (`auto`, or a designator such as `20` for Shift-JIS). The ECI is a
  hand-made choice about a text, and both the exported segments and the compaction fixture read it
  from here, so the two cannot disagree.
- `cases.tsv` holds the 112 case inputs. The case index names the expected modules,
  `verified/qrcodes/<case>.txt`.
- `sequences.tsv` holds the Structured Append cases (see below).

A manifest under `verified/` and the directory of dumps belonging to it share a name: `X.tsv`
describes every case, and `X/` dumps the sampled ones in full. `verified/texts.tsv` is the derived
counterpart of `input/texts.tsv` and carries only what the library measured: the character and UTF-8
byte counts, which catch a text mangled by a line-ending conversion or a stray byte-order mark.

`structuredappend.tsv` is the one fixture whose payloads are not the texts in `input/texts/`. Each
case names a generator, a length and a seed, which `RandomText` resolves. Add a case by appending a
row to `input/sequences.tsv` and regenerating. Nothing has to change in `VerifiedDataExport` or
`StructuredAppendTest`, which read whatever rows they find. A row whose length is `search` expands
in place into two. The exporter finds the longest payload that still fits into 16 QR codes and emits
it together with the next length up, which does not fit. Keep such rows last unless you mean to
renumber every case after them.

**Render snapshots** in `render/` are not auto-generated and no tool writes them. Change one by hand
only when the change is intended, and check the decode assertions still pass. `SvgTest` and `PngTest`
are their only readers; `RectangleTest` and `awt.QrCodeGraphicsTest` pin no files.

`PngTest` compares the PNG snapshot chunk by chunk, with the pixel data inflated first, rather than
as one byte array. The compressed bytes come from the JDK's `Deflater` and are not this library's to
promise across JDK versions. The pixels are.
