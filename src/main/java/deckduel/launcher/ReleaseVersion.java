package deckduel.launcher;

import java.util.Objects;

/**
 * A release version {@code MAJOR.MINOR.PATCH} with an optional pre-release label, for example
 * {@code 1.2.0} or {@code 1.2.0-beta.1}. Numbers compare as numbers ({@code 1.10.0} is newer
 * than {@code 1.9.0}) and a pre-release comes before its release.
 */
public record ReleaseVersion(int major, int minor, int patch, String label) implements Comparable<ReleaseVersion> {

    public ReleaseVersion {
        if (major < 0 || minor < 0 || patch < 0) throw new IllegalArgumentException("Negative version number");
        label = label == null || label.isEmpty() ? null : label;
    }

    /** Parses a version, rejecting anything else (such as "development build"). */
    public static ReleaseVersion parse(String text) {
        Objects.requireNonNull(text, "version");
        if (!text.matches("\\d{1,6}\\.\\d{1,6}\\.\\d{1,6}(-[0-9A-Za-z.-]{1,40})?"))
            throw new IllegalArgumentException("Not a release version: " + text);
        int dash = text.indexOf('-');
        String[] numbers = (dash < 0 ? text : text.substring(0, dash)).split("\\.");
        return new ReleaseVersion(Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]), Integer.parseInt(numbers[2]),
                dash < 0 ? null : text.substring(dash + 1));
    }

    /** Whether {@code text} is a release version. */
    public static boolean isValid(String text) {
        try { parse(text); return true; } catch (RuntimeException invalid) { return false; }
    }

    @Override public int compareTo(ReleaseVersion other) {
        int byNumber = major != other.major ? Integer.compare(major, other.major)
                : minor != other.minor ? Integer.compare(minor, other.minor) : Integer.compare(patch, other.patch);
        if (byNumber != 0) return byNumber;
        if (Objects.equals(label, other.label)) return 0;
        if (label == null) return 1;
        if (other.label == null) return -1;
        return compareLabels(label, other.label);
    }

    /** Dot-separated identifiers; numeric ones compare numerically and sort before words. */
    private static int compareLabels(String a, String b) {
        String[] left = a.split("\\."), right = b.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            boolean leftNumber = left[i].matches("\\d+"), rightNumber = right[i].matches("\\d+");
            int order = leftNumber && rightNumber ? Long.compare(Long.parseLong(left[i]), Long.parseLong(right[i]))
                    : leftNumber ? -1 : rightNumber ? 1 : left[i].compareTo(right[i]);
            if (order != 0) return order;
        }
        return Integer.compare(left.length, right.length);
    }

    /** Whether this version is newer than {@code other}. */
    public boolean newerThan(ReleaseVersion other) { return compareTo(other) > 0; }

    @Override public String toString() { return major + "." + minor + "." + patch + (label == null ? "" : "-" + label); }
}
