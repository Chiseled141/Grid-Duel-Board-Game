package onitama.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import onitama.core.Card;
import onitama.core.CardDeck;
import onitama.core.duel.RulesConfig;
import onitama.core.duel.SkillCategory;
import onitama.core.duel.SkillDefinition;
import onitama.core.duel.SkillParameter;

/**
 * Loads and validates balance configuration from UTF-8 properties files.
 * Supports bundled defaults with optional sparse external override files.
 */
public final class RulesConfigLoader {

    public static final String BUNDLED_CONFIG_PATH = "/config/game-balance.properties";

    private static final Map<String, SkillCategory> SKILL_CATEGORIES = Map.ofEntries(
            Map.entry("sidestep", SkillCategory.MOVEMENT),
            Map.entry("dash", SkillCategory.MOVEMENT),
            Map.entry("blink", SkillCategory.MOVEMENT),
            Map.entry("exchange", SkillCategory.MOVEMENT),
            Map.entry("advance", SkillCategory.MOVEMENT),
            Map.entry("recall", SkillCategory.MOVEMENT),
            Map.entry("strike", SkillCategory.ATTACK),
            Map.entry("pierce", SkillCategory.ATTACK),
            Map.entry("surround", SkillCategory.ATTACK),
            Map.entry("sweep", SkillCategory.ATTACK),
            Map.entry("shove", SkillCategory.CONTROL),
            Map.entry("pull", SkillCategory.CONTROL),
            Map.entry("vault", SkillCategory.MOVEMENT),
            Map.entry("flank", SkillCategory.ATTACK),
            Map.entry("transposition", SkillCategory.CONTROL),
            Map.entry("gust", SkillCategory.CONTROL),
            Map.entry("rally", SkillCategory.MOVEMENT),
            Map.entry("scatter", SkillCategory.MOVEMENT),
            Map.entry("crossfire", SkillCategory.ATTACK),
            Map.entry("chain-strike", SkillCategory.ATTACK),
            Map.entry("mana-surge", SkillCategory.RESOURCE),
            Map.entry("refocus", SkillCategory.RESOURCE),
            Map.entry("foresight", SkillCategory.RESOURCE),
            Map.entry("banish", SkillCategory.CONTROL)
    );

    private static final Set<String> ALLOWED_KEYS = buildAllowedKeys();

    private RulesConfigLoader() {}

    private static Set<String> buildAllowedKeys() {
        Set<String> set = new HashSet<>(Set.of(
                "config.schemaVersion",
                "rules.version",
                "movement.handSize",
                "skill.handSize",
                "mana.initial",
                "mana.income",
                "mana.cap",
                "turn.limit"
        ));
        for (String id : RulesConfig.CANONICAL_SKILL_IDS) {
            set.add("skill." + id + ".name");
            set.add("skill." + id + ".cost");
            set.add("skill." + id + ".copies");
            set.add("skill." + id + ".description");
            set.add("skill." + id + ".artwork");
        }
        set.add("skill.sidestep.distance");
        set.add("skill.dash.distance");
        set.add("skill.vault.distance");
        set.add("skill.pierce.distance");
        set.add("skill.blink.range");
        set.add("skill.exchange.range");
        set.add("skill.transposition.range");
        set.add("skill.crossfire.range");
        set.add("skill.sweep.maxTargets");
        set.add("skill.gust.maxTargets");
        set.add("skill.rally.maxTargets");
        set.add("skill.scatter.maxTargets");
        set.add("skill.surround.requiredAllies");
        set.add("skill.mana-surge.manaGain");
        set.add("skill.foresight.lookahead");
        return Set.copyOf(set);
    }

    /**
     * Loads rules configuration. If {@code overrideFile} is non-null, overlays its
     * properties on top of the bundled defaults.
     *
     * @param overrideFile optional path to override properties file, or null
     * @return validated immutable RulesConfig
     * @throws ConfigException on any missing, invalid, duplicate, or malformed setting
     */
    public static RulesConfig load(Path overrideFile) {
        Map<String, String> properties = loadProperties(overrideFile);
        return parseAndValidate(properties);
    }

    private static Map<String, String> loadProperties(Path overrideFile) {
        DuplicateDetectingProperties defaultProps = new DuplicateDetectingProperties("bundled defaults");
        try (InputStream in = RulesConfigLoader.class.getResourceAsStream(BUNDLED_CONFIG_PATH)) {
            if (in == null) {
                throw new ConfigException("bundledConfig", BUNDLED_CONFIG_PATH, "bundled resource not found");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                defaultProps.load(reader);
            }
        } catch (IOException e) {
            throw new ConfigException("bundledConfig", BUNDLED_CONFIG_PATH, "failed to read bundled defaults: " + e.getMessage(), e);
        }

        Map<String, String> merged = new HashMap<>();
        for (String key : defaultProps.stringPropertyNames()) {
            validateAllowedKey(key, "bundled defaults");
            merged.put(key, defaultProps.getProperty(key).trim());
        }

        if (overrideFile != null) {
            DuplicateDetectingProperties overrideProps = new DuplicateDetectingProperties(overrideFile.toString());
            try (BufferedReader reader = Files.newBufferedReader(overrideFile, StandardCharsets.UTF_8)) {
                overrideProps.load(reader);
            } catch (IOException e) {
                throw new ConfigException("overrideFile", overrideFile.toString(), "failed to read override file: " + e.getMessage(), e);
            }
            for (String key : overrideProps.stringPropertyNames()) {
                validateAllowedKey(key, overrideFile.toString());
                merged.put(key, overrideProps.getProperty(key).trim());
            }
        }

        return merged;
    }

    private static void validateAllowedKey(String key, String source) {
        if (!ALLOWED_KEYS.contains(key)) {
            throw new ConfigException(key, null, "unknown configuration key in " + source);
        }
    }

    private static RulesConfig parseAndValidate(Map<String, String> props) {
        int schemaVersion = parseInt(props, "config.schemaVersion");
        if (schemaVersion != 1) {
            throw new ConfigException("config.schemaVersion", String.valueOf(schemaVersion), "unsupported schema version, must be 1");
        }

        String rulesVersion = requireString(props, "rules.version");
        if (!RulesConfig.EXPECTED_RULES_VERSION.equals(rulesVersion)) {
            throw new ConfigException("rules.version", rulesVersion, "unsupported rules version, must be " + RulesConfig.EXPECTED_RULES_VERSION);
        }

        int movementHandSize = parseInt(props, "movement.handSize");
        if (movementHandSize != 2) {
            throw new ConfigException("movement.handSize", String.valueOf(movementHandSize), "movementHandSize must be exactly 2");
        }

        int manaCap = parseInt(props, "mana.cap");
        if (manaCap < 1 || manaCap > 20) {
            throw new ConfigException("mana.cap", String.valueOf(manaCap), "mana.cap must be 1..20");
        }

        int skillHandSize = parseInt(props, "skill.handSize");
        if (skillHandSize < 1 || skillHandSize > 6) {
            throw new ConfigException("skill.handSize", String.valueOf(skillHandSize), "skill.handSize must be 1..6");
        }

        int initialMana = parseInt(props, "mana.initial");
        if (initialMana < 0 || initialMana > manaCap) {
            throw new ConfigException("mana.initial", String.valueOf(initialMana), "mana.initial must be 0..mana.cap (" + manaCap + ")");
        }

        int manaIncome = parseInt(props, "mana.income");
        if (manaIncome < 1 || manaIncome > manaCap) {
            throw new ConfigException("mana.income", String.valueOf(manaIncome), "mana.income must be 1..mana.cap (" + manaCap + ")");
        }

        int turnLimit = parseInt(props, "turn.limit");
        if (turnLimit < 2 || turnLimit > 500) {
            throw new ConfigException("turn.limit", String.valueOf(turnLimit), "turn.limit must be 2..500");
        }

        List<SkillDefinition> skillDefs = new ArrayList<>();
        int totalCopies = 0;

        for (String id : RulesConfig.CANONICAL_SKILL_IDS) {
            String nameKey = "skill." + id + ".name";
            String costKey = "skill." + id + ".cost";
            String copiesKey = "skill." + id + ".copies";
            String descKey = "skill." + id + ".description";
            String artKey = "skill." + id + ".artwork";

            String name = requireString(props, nameKey);
            if (name.isBlank() || name.length() > 64) {
                throw new ConfigException(nameKey, name, "name must be nonblank and <= 64 chars");
            }

            int cost = parseInt(props, costKey);
            if (cost < 0 || cost > manaCap) {
                throw new ConfigException(costKey, String.valueOf(cost), "cost must be 0..mana.cap (" + manaCap + ")");
            }

            int copies = parseInt(props, copiesKey);
            if (copies < 0 || copies > 4) {
                throw new ConfigException(copiesKey, String.valueOf(copies), "copies must be 0..4");
            }
            totalCopies += copies;

            String description = requireString(props, descKey);
            if (description.isBlank() || description.length() > 1024) {
                throw new ConfigException(descKey, description, "description must be nonblank and <= 1024 chars");
            }

            String artwork = requireString(props, artKey);
            if (artwork.isBlank() || artwork.length() > 256) {
                throw new ConfigException(artKey, artwork, "artwork path must be nonblank and <= 256 chars");
            }
            if (artwork.startsWith("/") || artwork.contains("..")) {
                throw new ConfigException(artKey, artwork, "artwork path must be classpath-relative without leading slash or '..'");
            }

            Map<SkillParameter, Integer> params = parseSkillParameters(id, props, manaCap);
            SkillText.validatePlaceholders(description, params, descKey);

            SkillCategory category = SKILL_CATEGORIES.get(id);
            skillDefs.add(new SkillDefinition(id, name, category, cost, copies, params, description, artwork));
        }

        if (2 * skillHandSize > totalCopies) {
            throw new ConfigException("skill.handSize", String.valueOf(skillHandSize),
                    "not enough skill copies for starting deal: 2 * " + skillHandSize + " > " + totalCopies);
        }

        List<Card> movementCards = CardDeck.catalog();
        try {
            return new RulesConfig(rulesVersion, movementHandSize, skillHandSize,
                    initialMana, manaIncome, manaCap, turnLimit, movementCards, skillDefs);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("rulesConfig", null, e.getMessage(), e);
        }
    }

    private static Map<SkillParameter, Integer> parseSkillParameters(String id, Map<String, String> props, int manaCap) {
        Map<SkillParameter, Integer> map = new HashMap<>();
        switch (id) {
            case "sidestep", "dash", "vault", "pierce" -> {
                String k = "skill." + id + ".distance";
                int dist = parseInt(props, k);
                if (dist < 1 || dist > 6) {
                    throw new ConfigException(k, String.valueOf(dist), "distance must be 1..6");
                }
                map.put(SkillParameter.DISTANCE, dist);
            }
            case "blink", "exchange", "transposition" -> {
                String k = "skill." + id + ".range";
                int range = parseInt(props, k);
                if (range < 1 || range > 12) {
                    throw new ConfigException(k, String.valueOf(range), "range must be 1..12");
                }
                map.put(SkillParameter.RANGE, range);
            }
            case "crossfire" -> {
                String k = "skill.crossfire.range";
                int range = parseInt(props, k);
                if (range < 1 || range > 6) {
                    throw new ConfigException(k, String.valueOf(range), "crossfire range must be 1..6");
                }
                map.put(SkillParameter.RANGE, range);
            }
            case "sweep", "gust", "rally", "scatter" -> {
                String k = "skill." + id + ".maxTargets";
                int targets = parseInt(props, k);
                if (targets < 1 || targets > 2) {
                    throw new ConfigException(k, String.valueOf(targets), "maxTargets must be 1..2");
                }
                map.put(SkillParameter.MAX_TARGETS, targets);
            }
            case "surround" -> {
                String k = "skill.surround.requiredAllies";
                int allies = parseInt(props, k);
                if (allies < 1 || allies > 4) {
                    throw new ConfigException(k, String.valueOf(allies), "requiredAllies must be 1..4");
                }
                map.put(SkillParameter.REQUIRED_ALLIES, allies);
            }
            case "mana-surge" -> {
                String k = "skill.mana-surge.manaGain";
                int gain = parseInt(props, k);
                if (gain < 1 || gain > manaCap) {
                    throw new ConfigException(k, String.valueOf(gain), "manaGain must be 1..mana.cap (" + manaCap + ")");
                }
                map.put(SkillParameter.MANA_GAIN, gain);
            }
            case "foresight" -> {
                String k = "skill.foresight.lookahead";
                int look = parseInt(props, k);
                if (look < 1 || look > 5) {
                    throw new ConfigException(k, String.valueOf(look), "lookahead must be 1..5");
                }
                map.put(SkillParameter.LOOKAHEAD, look);
            }
            default -> {}
        }
        return Map.copyOf(map);
    }

    private static String requireString(Map<String, String> props, String key) {
        String val = props.get(key);
        if (val == null || val.isBlank()) {
            throw new ConfigException(key, val, "missing required setting");
        }
        return val;
    }

    private static int parseInt(Map<String, String> props, String key) {
        String val = requireString(props, key);
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new ConfigException(key, val, "expected integer value: " + e.getMessage(), e);
        }
    }

    private static class DuplicateDetectingProperties extends Properties {
        private final Set<Object> seenKeys = new HashSet<>();
        private final String sourceName;

        DuplicateDetectingProperties(String sourceName) {
            this.sourceName = sourceName;
        }

        @Override
        public synchronized Object put(Object key, Object value) {
            if (!seenKeys.add(key)) {
                throw new ConfigException(String.valueOf(key), String.valueOf(value),
                        "duplicate key in " + sourceName);
            }
            return super.put(key, value);
        }
    }
}
