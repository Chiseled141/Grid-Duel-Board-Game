package deckduel.release;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Finds which platform a game jar's JavaFX native libraries were built for, by reading the
 * header of the bundled glass library (Mach-O, PE or ELF), so a package is never labelled for a
 * platform whose natives it does not carry.
 */
final class NativeLibraries {
    private NativeLibraries() { }

    /** The platform id of the jar's JavaFX natives, or empty when the jar has none. */
    static Optional<String> platformOf(JarFile jar) throws IOException {
        for (String[] candidate : new String[][] {{"libglass.dylib", "mac"}, {"glass.dll", "windows"}, {"libglass.so", "linux"}}) {
            ZipEntry entry = jar.getEntry(candidate[0]);
            if (entry == null) continue;
            try (InputStream in = jar.getInputStream(entry)) {
                return Optional.of(candidate[1] + "-" + architecture(in.readNBytes(4096)));
            }
        }
        return Optional.empty();
    }

    /** The processor a native library's header names: x64, aarch64, universal or unknown. */
    static String architecture(byte[] header) {
        if (header.length >= 8 && u32(header, 0) == 0xFEEDFACFL) {
            long cpu = u32(header, 4);
            return cpu == 0x0100000CL ? "aarch64" : cpu == 0x01000007L ? "x64" : "unknown";
        }
        if (header.length >= 4 && (header[0] & 0xFF) == 0xCA && (header[1] & 0xFF) == 0xFE
                && (header[2] & 0xFF) == 0xBA && (header[3] & 0xFF) == 0xBE) return "universal";
        if (header.length >= 0x40 && header[0] == 'M' && header[1] == 'Z') {
            int pe = (int) u32(header, 0x3C);
            if (pe > 0 && pe + 6 <= header.length && header[pe] == 'P' && header[pe + 1] == 'E') {
                int machine = u16(header, pe + 4);
                return machine == 0x8664 ? "x64" : machine == 0xAA64 ? "aarch64" : "unknown";
            }
        }
        if (header.length >= 20 && header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
            int machine = u16(header, 18);
            return machine == 0x3E ? "x64" : machine == 0xB7 ? "aarch64" : "unknown";
        }
        return "unknown";
    }

    private static long u32(byte[] b, int at) {
        return (b[at] & 0xFFL) | (b[at + 1] & 0xFFL) << 8 | (b[at + 2] & 0xFFL) << 16 | (b[at + 3] & 0xFFL) << 24;
    }

    private static int u16(byte[] b, int at) { return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8; }
}
