/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

/**
 * The two views of a single mask pattern: the mask as stored ({@code rows}) and its transpose
 * ({@code columns}).
 * <p>
 * A mask pair is cached per (mask pattern, version) and XORed into a {@link ScoringMatrix} as a
 * unit, so the scoring matrix and its transpose stay in sync. The contained matrices are shared and
 * cached; callers must not mutate them.
 * </p>
 *
 * @param rows    the mask as stored
 * @param columns the transpose of the mask
 */
record MaskPair(BitMatrix rows, BitMatrix columns) {
}
