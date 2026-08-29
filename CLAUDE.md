# CLAUDE.md

## Purpose

QR Code Press is a Java library that generates QR codes according to ISO/IEC 18004.
It is fast, has no runtime dependencies and needs only `java.base`. It does not draw
images with a graphics library. It hands the caller a list of rectangles, an SVG document,
a graphics path or a PNG, and lets the application take it from there.

The library will eventually be published as a public project on GitHub
and on Maven Central. But so far, it has not. Major changes are still possible.
Backward-compatibility is no concern yet.

The library started as a port of the .NET library [QrCodeGenerator](https://github.com/manuelbl/QrCodeGenerator).

## Repository layout

There is **no root aggregator pom**. Every subdirectory is an independent Maven project with its
own wrapper.

Each example under `examples/` is an independent Maven project too, resolving the library from
the local repository by version property. An example is therefore built after `./mvnw install`
in `qr-code-press/`, and nothing has to be registered anywhere in the *build* when one is added,
since no Maven project ever sees the examples. CI is the exception. It compiles and runs each
example from a step of its own, and a new example has to be added there.

`release/set-version.sh` is the only thing that edits a version string. It discovers the poms
rather than listing them, so an added example needs no change there either. See
[RELEASING.md](RELEASING.md).

`profiling/` is an independent Maven project as well and resolves the library the same way, so a
library change has to be installed before it can be measured. It also depends on ZXing and qrcodegen,
which it benchmarks the library against. It is not part of the library build and CI only compiles it.

Build and test from within the library directory:

```sh
cd qr-code-press
./mvnw verify
```

`verify` runs the tests and lints the javadoc, failing on any javadoc warning.

## Architecture

`QrCode` is nearly the whole public API. Static factory methods and a builder create immutable
instances, which render to several output formats. Everything else is package-private.

### Encoding pipeline

Text or bytes → segments → codewords → module matrix.

1. **`DataSegment.fromText` / `fromBinary`** chooses the text encoding. With automatic ECI it
   tries ISO-8859-1 and adds no ECI segment; if that is lossy it falls back to UTF-8 with an ECI
   segment. Segments hold a `ByteSlice` view into an array the *library* owns, so splitting a
   payload copies nothing and a caller can never mutate a segment's payload afterwards.
2. **`SegmentCompaction`** assigns each byte the cheapest mode (numeric, alphanumeric, Kanji,
   binary), groups consecutive bytes into blocks, then merges adjacent blocks while merging
   shortens the bit stream. Merge cost depends on the version, because the character count
   indicator changes width at versions 1, 10 and 27. This produces the "smallest possible QR code"
   claim.
3. **`QrCodeBuilder.build`** wires the remaining stages, each a package-private static class:
   - **`VersionPlanner.plan`** picks the smallest version that fits, then raises the error
     correction level for free if that does not need a larger version.
   - **`Codewords.buildData`** turns the segments into a `BitStream` and that into data codewords,
     terminator and 0xEC/0x11 padding.
   - **`Codewords.addErrorCorrection`** splits the data into blocks, computes Reed-Solomon
     codewords (`ReedSolomon`) and interleaves data and ECC per spec. `ReedSolomon` precomputes
     every multiple of the generator polynomial per capacity, so the division is a table lookup and
     a shift-and-XOR of the remainder, eight coefficients per machine word, with no field
     arithmetic in the loop.
   - **`MatrixEncoder.encode`** builds the symbol with `ScoringMatrix.ofSymbol`. `FixedPatterns`
     draws the version's fixed patterns and, in the same walk, builds the reserved-module mask,
     whose complement is the payload-area map. The codewords then go into the free modules through
     the version's precomputed table of `BitMatrix` addresses, so the zigzag is walked once per
     version, not once per encode. Each of the eight mask patterns is applied and scored by
     `Penalty`, and the lowest-scoring one wins.

The shared ISO/IEC 18004 lookup tables live in `QrCodeParameters`, with the table numbers in the
comments.

### `BitMatrix` and the transpose

`BitMatrix` is the central data structure, a square bit grid stored row-major with each row
starting at a word boundary. It offers whole-matrix `and` / `xor` / `invert` / `popCount` and an
in-place 64×64 delta-swap transpose.

A row takes one of **three layouts**, chosen by the size alone: one `long` of modules per 64
columns. One word covers versions 1 to 11 and so most QR codes, two covers versions 12 to 27, three
covers versions 28 to 40. `usedWordsPerRow()` reports that count and is what an algorithm reading
the bits dispatches on; `BitMatrix`'s own javadoc is the normative statement of the bands and the
one place they are written down. The maximum size is 192, which covers version 40 at 177 modules.

The *stride* from one row to the next, `wordsPerRow()`, is that count rounded up to a power of two,
so a row index is a shift rather than a multiplication. It differs only for a three-word row, which
is allocated a fourth, always-zero padding word — which is why whole-matrix operations (`and`,
`xor`, `popCount`) run flat over `raw()` instead of skipping it, and stay vectorizable. The layout
is a property of the value, so two matrices of the same size always agree on it and an `and`/`xor`
can never mix them. It exists for `Penalty`, which scans fewer words the narrower the row.

Every penalty rule that scans *columns* works by transposing and reusing the *row* algorithm, so
the transpose is required, not a convenience. `ScoringMatrix` owns that. It holds a matrix and its
transpose as two views of one value and updates both on every mutation, so they cannot drift apart.
`MaskPair` is the same idea for a mask pattern.

`Penalty` works on whole `long` words rather than module by module, and stops early once the
running score exceeds the best score so far. Every rule subtracts what the three finder patterns
contribute, so a rule scores non-negatively only for a real symbol, and only then is the early stop
sound. `ScoringMatrix` owns that precondition. Its only factory, `ofSymbol`, builds the matrix from
the version's fixed patterns, so a grid without finder patterns cannot reach `Penalty` at all.
Every rule that scans rows has three forms, one per `BitMatrix` layout, each with its loop over the
words of a row unrolled; all three compute the same score, and `PenaltyTest` holds them to one
module-by-module oracle at sizes either side of 64 and of 128. The finder-pattern rule is the
exception to the triplication: its bit identity lives once in `matchesInWord`, which each form calls
as many times as its layout has words, since only the neighbouring words it is passed differ.

### Performance-tuned constants

A separate project determined two orderings: `MatrixEncoder.PATTERN_EVALUATION_ORDER` (evaluate
likely winners first, so the early-stop bound tightens sooner) and the rule order inside `Penalty`.
Changing them changes speed, not output.

`LazyCache` caches the per-version results, in an `AtomicReferenceArray` keyed by a dense small
integer, either version 1 to 40 or mask × version. The fixed patterns, the payload-area map, the
payload target table and the mask pairs all live there. **Cached `BitMatrix` instances are shared
and must never be mutated**; callers copy first.

### Rendering

`RectangleBuilder` merges adjacent dark modules into the largest possible non-overlapping
rectangles, whose union is exactly the dark modules. It is the single source of that geometry.
`QrCode.toRectangles()` publishes the list, and `SvgBuilder` (SVG document and SVG/XAML path)
consumes the same list, adding the border when emitting.

`PngBuilder` writes a 1-bit indexed PNG with a two-entry palette, using only
`java.util.zip.Deflater` and `CRC32`. That keeps the core on `java.base`, so the library works on
Android, in minimal `jlink` images and in headless containers.

The optional `net.codecrete.qrcodepress.awt` package adds `BufferedImage` and `Graphics2D` helpers
for callers already in AWT or Swing. It is the only part needing `java.desktop`, declared
`requires static` in `module-info.java`, and it is the only place that honours a translucent colour
(the PNG encoder writes no `tRNS` chunk).

### Structured Append and diagnostics

- `StructuredAppend` splits a payload across up to 16 linked QR codes, spread as evenly as possible
  over the fewest codes. `QrCodeSequence` is a separate builder type, so options that mean nothing
  for a sequence are simply not there. The options the two builders *do* share live in
  `EncodingOptions`, which owns their validation; each builder keeps its own fluent methods,
  javadoc and return type and delegates to it.
- `EncodingInfo` and `PenaltyScore` are opt-in diagnostics, returned by
  `QrCodeBuilder.buildWithDiagnostics()`. Collecting them disables the penalty early-stop and is
  slower, which is why it is a separate terminal method. The forced mask, in contrast, is an
  ordinary builder input (`forceMask`), not part of the diagnostics.

## Conventions

- See `.editorconfig` for formatting.
- Acronyms follow modern Java style: `Eci`, not `ECI`.
- Every public type and member carries javadoc; package-private types do too, since they carry most
  of the reasoning. `./mvnw verify` fails on a javadoc warning.
- Exceptions are unchecked and rooted in JDK types: `DataTooLongException` and `EciException` both
  extend `IllegalArgumentException`; `null` arguments throw `NullPointerException` via
  `Objects.requireNonNull`.
- Byte payloads are copied **once**, when they cross the public API boundary. Internal code is then
  free to alias them.

## Tests

JUnit 6 with AssertJ. The strategy is characterization plus cross-validation, not unit tests alone.

The test resources are split by who writes them, and the directory is the whole rule.
`src/test/resources/input/` is hand-written and only ever read: `texts.tsv` and the payload texts it
names, `cases.tsv` (the 112 case parameters) and `sequences.tsv` (the Structured Append cases).
`src/test/resources/verified/` is written by `VerifiedDataExport` and **must never be edited by hand**.
`src/test/resources/render/` is the third kind, expected output with no generator, changed by hand
when a rendering change is intended. Within `verified/`, a manifest and the dumps belonging to it
share a name: `X.tsv` describes every case, and `X/` dumps the sampled ones in full.

## Where to look

| Question | Document |
|---|---|
| What is the library, what is the public API, what was decided | [docs/spec.md](docs/spec.md) |
| What do the domain terms mean | [CONTEXT.md](CONTEXT.md) |
| How is the verified data produced | `VerifiedDataExport` in the test tree, and `.claude/skills/updating-test-data` |
| How is the library released to Maven Central | [RELEASING.md](RELEASING.md) |
| How is performance measured, and what did it measure before | [profiling/README.md](profiling/README.md) |
| How does the library compare to ZXing and qrcodegen | [profiling/COMPARISON.md](profiling/COMPARISON.md) |
| How are the tests structured | [qr-code-press/src/test/CLAUDE.md](qr-code-press/src/test/CLAUDE.md) |
