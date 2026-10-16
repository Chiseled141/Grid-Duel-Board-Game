package onitama.config;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import onitama.core.duel.SkillDefinition;
import onitama.core.duel.SkillParameter;

/**
 * Renders live skill descriptions by substituting placeholders from definition parameters.
 */
public final class SkillText {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    private SkillText() {}

    /**
     * Maps parameter enum to placeholder name.
     */
    public static String placeholderFor(SkillParameter parameter) {
        return switch (parameter) {
            case DISTANCE -> "distance";
            case RANGE -> "range";
            case MAX_TARGETS -> "maxTargets";
            case REQUIRED_ALLIES -> "requiredAllies";
            case MANA_GAIN -> "manaGain";
            case LOOKAHEAD -> "lookahead";
        };
    }

    /**
     * Maps placeholder name to parameter enum, or null if unknown.
     */
    public static SkillParameter parameterFor(String placeholder) {
        return switch (placeholder) {
            case "distance" -> SkillParameter.DISTANCE;
            case "range" -> SkillParameter.RANGE;
            case "maxTargets" -> SkillParameter.MAX_TARGETS;
            case "requiredAllies" -> SkillParameter.REQUIRED_ALLIES;
            case "manaGain" -> SkillParameter.MANA_GAIN;
            case "lookahead" -> SkillParameter.LOOKAHEAD;
            default -> null;
        };
    }

    /**
     * Validates that all placeholders in the description template correspond to
     * parameters present in the given parameter map.
     *
     * @throws ConfigException if an unknown or unsupplied placeholder is found
     */
    public static void validatePlaceholders(String template, Map<SkillParameter, Integer> parameters, String key) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            SkillParameter param = parameterFor(placeholder);
            if (param == null) {
                throw new ConfigException(key, template, "unknown placeholder: {" + placeholder + "}");
            }
            if (!parameters.containsKey(param)) {
                throw new ConfigException(key, template, "unresolved placeholder {" + placeholder + "}: parameter not configured for this skill");
            }
        }
    }

    /**
     * Substitutes supported placeholders using the definition's parameter values.
     */
    public static String render(SkillDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        String text = definition.descriptionTemplate();
        for (Map.Entry<SkillParameter, Integer> entry : definition.parameters().entrySet()) {
            String token = "{" + placeholderFor(entry.getKey()) + "}";
            text = text.replace(token, String.valueOf(entry.getValue()));
        }
        return text;
    }
}
