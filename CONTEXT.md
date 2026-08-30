# QR Code Press Context

The domain language of this library. These terms come from the QR code standard and from this
library's own pipeline. Use them consistently in code, comments and discussion.

## Language

**Module**
: A single square of a QR code, dark or light. This is the unit the standard counts by.
*Avoid*: pixel (keep "pixel" for rendered output), cell, dot.

**Version**
: The size of a QR code, 1 to 40. Version 1 is 21×21 modules, version 40 is 177×177; the size is
always `4 × version + 17`. Bigger versions hold more data.
*Avoid*: "size" when you mean the version, and vice versa. `getVersion()` and `getSize()` return
different numbers.

**Fixed patterns**
: The modules that a given version places identically regardless of the payload: finder patterns,
separators, timing patterns, alignment patterns and version information, plus the area reserved
for the format information.
*Avoid*: "function patterns", the ISO/IEC 18004 term. We say fixed patterns.

**Footprint**
: The area a fixed pattern occupies, whatever the colour of the modules inside it. A footprint is
reserved even where its modules are light: separators, the light rings of a finder, `0` bits of the
format and version information.

**Reserved modules**
: The union of all fixed-pattern footprints. Every module the payload must not use.

**Payload-area map**
: The complement of the reserved modules, the ones the payload zig-zag may fill.
*Avoid*: "data mask", which means the mask pattern.

**Mask pattern**
: One of the eight XOR patterns (index 0 to 7) applied to the payload area. The one with the lowest
penalty score wins. Exposed as `QrCode.getMask()`.

**Scoring matrix**
: A QR code symbol paired with its transpose, kept in sync, used while selecting the mask pattern.
Its `rows()` view is the matrix as stored; its `columns()` view is the transpose. Penalty rules
that scan rows read `rows()`, rules that scan columns read `columns()`, so the column rules reuse
the row algorithm instead of duplicating it. Every mutation updates both views together. It is
built from the version's fixed patterns, so it always carries the three finder patterns the
penalty rules subtract, which is what makes their early stop sound.
*Avoid*: treating the transpose as a second copy the caller keeps in sync by hand, or scoring a bit
grid that is not a symbol. `ScoringMatrix` owns both invariants.

**Mask pair**
: A mask pattern and its transpose, the two views cached per (mask pattern, version) and XORed into
a scoring matrix as a unit.

**Codewords**
: The 8-bit symbols the payload becomes once a version and error correction level are chosen. Data
codewords (segment bits, terminator, padding) followed by Reed-Solomon error correction codewords,
interleaved per spec, ready to be filled into the matrix.

**Data segment**
: A run of the payload encoded in one mode. A QR code's payload is a list of segments, and the same
text can be split in many ways, some shorter than others.

**Data segment mode**
: How a segment encodes its payload: numeric, alphanumeric, Kanji or binary. Two further modes have
a function rather than a payload: ECI and Structured Append.

**Data segment mode info**
: The per-mode rules of a `DataSegmentMode`: mode indicator, character count indicator widths, and
for the four data modes the bit-length and byte-count formulas plus the segment factory. Each mode
carries its own, as an overriding enum body.
*Avoid*: re-deriving per-mode behaviour with a `switch` or with arithmetic on the enum ordinal at
each call site.

**Compaction**
: Choosing the segmentation with the shortest bit stream: assign each byte its cheapest mode, group
consecutive bytes into blocks, then merge adjacent blocks while merging shortens the stream. This
is what "smallest possible QR code" means in this library.
*Avoid*: "optimization" as a name for this step. It is about segment boundaries in particular.

**Slice**
: A view of a range of a byte array (`ByteSlice`). Segments refer to their payload with a slice, so
splitting a payload copies nothing. The array is one the library owns and never modifies.

**Encoding options**
: The options both builders accept: the error correction level and whether it may be boosted, the
version range, and the ECI designator with an optional character set. They are one value
(`EncodingOptions`) that owns the rules going with them: what a version range is, and that
`Eci.AUTOMATIC` cannot be paired with an explicit character set. Each builder keeps its own fluent
methods, its own javadoc and its own return type, and validates through it.
*Avoid*: restating one of those rules in a builder. An option that means something to only one
builder, such as the Kanji strategy or the forced mask, stays on that builder and is not an encoding
option.

**Structured Append**
: A sequence of up to 16 QR codes carrying one payload between them. Each code starts with a
Structured Append segment giving its position, the sequence length and the parity of the whole
payload, so a scanner can reassemble the parts and notice they belong together.

**Border** / **quiet zone**
: The light margin around a QR code, measured in modules. The standard asks for at least four. In
the API it is the `border` parameter of every render method.

**Outline**
: The dark modules traced as closed polygons: one clockwise loop around each group of modules that
touch horizontally or vertically, one counterclockwise loop around each hole in a group (most
notably the light ring of a finder pattern). Diagonal touch does not connect. The opposite winding
means filling all loops as one path yields the QR code under both the nonzero and the even-odd
fill rule. Exposed as `QrCode.toOutlines()`; the SVG document, the graphics path and the AWT
`draw` are built from it, so adjacent shapes cannot show hairline seams.
*Avoid*: contour.

## Relationships

- The pipeline is: text or bytes → **data segments** → **codewords** → **module** matrix. A
  **version** and error correction level are planned first, then the **codewords** are built, then
  filled into the matrix, then a **mask pattern** is chosen.
- A **version** fully determines the **fixed patterns**, and therefore the **reserved modules** and
  the **payload-area map**.
- The **reserved modules** are the union of every fixed-pattern **footprint**; the
  **payload-area map** is their complement.
- Every dark **module** a fixed pattern draws lies within the **reserved modules**. Everything else
  rests on that invariant: drawn ⊆ reserved.
- A **mask pattern** is XORed only over the **payload-area map**, never over **reserved modules**.
- Choosing the **mask pattern** scores one **scoring matrix** per candidate. The column penalty
  rules read its `columns()` view, and each candidate is applied as a **mask pair**.
- Both builders hold **encoding options**. `QrCodeSequenceBuilder` hands a *copy* to a
  `QrCodeBuilder` for each QR code of the sequence, so the two never share state.
- **Compaction** depends on the **version**, because the character count indicator changes width at
  versions 1, 10 and 27. The library therefore compacts the segments for the largest acceptable
  version, since the version is not settled until the segments are known.

## Example dialogue

> **Dev:** "If I move an alignment pattern, do I update the drawn matrix and the reserved modules
> separately?"
>
> **Domain expert:** "No, they are two views of the same **fixed patterns**. One walk emits both.
> It stamps the dark **modules** and reserves the **footprint** in the same place. You cannot infer
> reserved from drawn, because a footprint reserves light modules too."
