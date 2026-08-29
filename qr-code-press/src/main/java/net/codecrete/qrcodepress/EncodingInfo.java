/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.List;

/**
 * A QR code together with details about how it was encoded.
 * <p>
 * The details are collected for analysis only; the library itself does not need them. They are
 * returned by {@link QrCodeBuilder#buildWithDiagnostics()}, which is slower than
 * {@link QrCodeBuilder#build()} because every mask pattern has to be scored in full.
 * </p>
 *
 * @param qrCode    the QR code
 * @param penalties the penalty score of each of the eight data mask patterns, indexed by pattern;
 *                  the QR code uses the pattern with the lowest total, unless one was pinned with
 *                  {@link QrCodeBuilder#forceMask(int)}
 * @param segments  the data segments the payload was encoded as
 */
public record EncodingInfo(QrCode qrCode, List<PenaltyScore> penalties, List<DataSegment> segments) {
}
