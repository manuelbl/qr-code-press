/**
 * QR Code Press, a fast and easy QR code generator.
 */
module net.codecrete.qrcodepress {
    // needed by the AWT bridge alone, and only when the caller uses that package
    requires static java.desktop;

    exports net.codecrete.qrcodepress;
    exports net.codecrete.qrcodepress.awt;
}
