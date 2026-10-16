package onitama.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable definition of a skill card.
 */
public record SkillDefinition(
        String id,
        String name,
        SkillCategory category,
        int manaCost,
        int copies,
        Map<SkillParameter, Integer> parameters,
        String descriptionTemplate,
        String artworkPath) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SkillDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(descriptionTemplate, "descriptionTemplate must not be null");
        Objects.requireNonNull(artworkPath, "artworkPath must not be null");
        parameters = Map.copyOf(parameters);
    }
}
