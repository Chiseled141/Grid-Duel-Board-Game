package deckduel.core.card;

/** Immutable King catalog, transcribed from the seven supplied paintings; outside the draw deck. */
public enum King {
    VARKHAN("ashkar-varkhan", "Varkhan", "Ashkar", "Assault",
            "Each Assassin or Bowman with active tags grants +2 points. Maximum +10.", 2, 10, 0),
    ALTHARION("asterion-altharion", "Altharion", "Asterion", "Royal Council",
            "Each different class with active tags grants +2 points. Maximum +10.", 2, 10, 0),
    MORDRAVEN("dravorn-mordraven", "Mordraven", "Dravorn", "Legion",
            "In the largest active class group, each Unit from the third onward grants +3 points. Maximum +10.", 3, 10, 2),
    NETHRION("nerath-nethrion", "Nethrion", "Nerath", "Secret Edict",
            "On your first swap each round, privately look at the top 2 for its last card; keep 1, put the other on the bottom.", 0, 0, 2),
    AURENDOR("solmara-aurendor", "Aurendor", "Solmara", "Royal Decree",
            "Each Spell you played this round grants +3 points, maximum +9, while your row has a Unit with active tags.", 3, 9, 0),
    THALVAREN("sylvaren-thalvaren", "Thalvaren", "Sylvaren", "Alliance",
            "Each different nation with active tags grants +2 points. Maximum +10.", 2, 10, 0),
    VAELTHOR("vaelora-vaelthor", "Vaelthor", "Vaelora", "Twin Banners",
            "Each nation with at least 2 Units with active tags grants +5 points. Maximum +10.", 5, 10, 2);

    private final String id, title, nation, ability, description;
    private final int points, cap, threshold;

    King(String id, String title, String nation, String ability, String description, int points, int cap, int threshold) {
        this.id = id; this.title = title; this.nation = nation; this.ability = ability;
        this.description = description; this.points = points; this.cap = cap; this.threshold = threshold;
    }
    /** Stable catalog and artwork ID. */
    public String id() { return id; }
    /** Name printed on the supplied painting. */
    public String title() { return title; }
    /** King's nation; never a tag added to the Unit row. */
    public String nation() { return nation; }
    /** Ability's printed name. */
    public String ability() { return ability; }
    /** Accessible rules text, independent of artwork. */
    public String description() { return description; }
    /** Points per eligible contribution. */
    public int points() { return points; }
    /** Maximum flat bonus. */
    public int cap() { return cap; }
    /** Group offset, qualifying group size, or Edict look count, depending on ability. */
    public int threshold() { return threshold; }
}
