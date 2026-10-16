package onitama.client;

/** Available local menu scenes, with stable preference keys and bundled media. */
public enum MenuTheme {
    VALLEY("Mountain valley", "menu-valley.gif", "cinematic-valley.png"),
    GOLDEN_FIELDS("Golden fields", "menu-battlefield.gif", "battlefield.png");

    private final String label;
    private final String clip;
    private final String still;

    MenuTheme(String label, String clip, String still) {
        this.label = label;
        this.clip = clip;
        this.still = still;
    }

    /** Human-readable scene name for the menu picker. */
    public String label() { return label; }
    /** Bundled animated GIF filename. */
    public String clip() { return clip; }
    /** Bundled still-image fallback filename. */
    public String still() { return still; }

    /** Bundled Java Sound track for this scene. */
    public String music() { return this == GOLDEN_FIELDS ? "/audio/golden-fields.wav" : "/audio/menu-music.wav"; }

    /** Missing or unknown preferences use Golden fields. */
    public static MenuTheme fromPreference(String value) {
        if (value != null) {
            try { return valueOf(value); }
            catch (IllegalArgumentException ignored) { /* Keep settings from breaking startup. */ }
        }
        return GOLDEN_FIELDS;
    }
}
