package deckduel.core.duel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * A concrete instance of a skill card in hand, deck, or spent pile.
 */
public record CardInstance(String instanceId, String definitionId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public CardInstance {
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        Objects.requireNonNull(definitionId, "definitionId must not be null");
        if (definitionId.length() > 64 || instanceId.length() > 64
                || !RulesConfig.CANONICAL_SKILL_IDS.contains(definitionId)) {
            throw new IllegalArgumentException("invalid skill identity");
        }
        // The supported snapshot allows at most four copies, with canonical 1-based IDs.
        String prefix = definitionId + "#";
        if (!instanceId.startsWith(prefix) || instanceId.length() != prefix.length() + 1
                || instanceId.charAt(prefix.length()) < '1'
                || instanceId.charAt(prefix.length()) > '4') {
            throw new IllegalArgumentException("invalid skill instance: " + instanceId);
        }
    }

    @Override
    public String toString() {
        return instanceId;
    }
}
