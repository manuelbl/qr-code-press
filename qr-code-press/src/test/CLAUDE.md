# Tests

Test strategy for QR Code Press. The rules about which test-resource directory may be
written by hand live in the root `CLAUDE.md`.

- **Verified data tests** (`VerifiedDataTest`, `VerifiedData`). 112 cases in
  `src/test/resources/verified/`, asserting the exact module layout, version, ECC and mask. The
  data has been verified once and is the baseline everything else is tested against.
  `VerifiedDataExport`, a Java program in the test tree, regenerates it, and
  `VerifiedDataExportTest` asserts that
  regenerating reproduces the committed bytes exactly, the whole of `verified/` with nothing
  subtracted, which is what the input split buys. That exporter runs the library under test, so
  re-running it re-derives the data rather than cross-checking it. The independent checks are the
  ZXing round-trip and the Reed-Solomon cross-check below.
- **ZXing round-trip** (`ZxingRoundTripTest`). Every generated QR code is decoded with
  `com.google.zxing:core` and must round-trip with zero errors corrected. A module-flip negative
  control proves the assertion has teeth.
- **Reed-Solomon cross-check** (`ReedSolomonTest`). ECC codewords against ZXing's independent
  `ReedSolomonEncoder`.
- **Render snapshots** (`SvgTest`, `PngTest`). Committed expected output in
  `src/test/resources/render/`, hand-made and hand-maintained; no exporter writes it. `RectangleTest`
  and `awt.QrCodeGraphicsTest` pin no files and assert invariants instead. Every rendered image is
  also decoded again, pixel by pixel and through ZXing's detector, which is what proves the quiet
  zone and scale land where a scanner expects them.
- **Capacity tests**. The exact maximum payload per (version, ECC, mode), and that one byte more
  throws `DataTooLongException`. This pins the transcribed ISO/IEC 18004 tables.
- **Stage fixtures**. Segment compaction, codewords, fixed patterns, mask patterns and the
  Structured Append split are each pinned against the verified test data, so a defect surfaces in the
  stage that caused it rather than six steps later as a wrong QR code. The split fixture is the only
  one whose payloads are not the texts in `input/texts/`. Those are too short for a sequence, so a
  case in `input/sequences.tsv` names a generator, a length and a seed that `RandomText` resolves.
