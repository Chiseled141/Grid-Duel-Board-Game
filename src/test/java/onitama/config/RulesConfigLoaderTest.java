package onitama.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import onitama.core.duel.RulesConfig;
import onitama.core.duel.SkillDefinition;
import onitama.core.duel.SkillParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesConfigLoaderTest {

    @Test
    @DisplayName("Load bundled defaults matches documented balance specification")
    void loadBundledDefaults() {
        RulesConfig config = RulesConfigLoader.load(null);
        assertNotNull(config);
        assertEquals("grid-duel-v4", config.rulesVersion());
        assertEquals(0, config.movementHandSize());
        assertEquals(3, config.skillHandSize());
        assertEquals(2, config.initialMana());
        assertEquals(1, config.manaPerTurn());
        assertEquals(5, config.manaCap());
        assertEquals(60, config.turnLimit());
        assertEquals(8, config.scoreToWin());
        assertEquals(0, config.movementCards().size());
        assertEquals(30, config.skills().size());

        // Check rendered text for Sidestep
        SkillDefinition sidestep = config.skills().stream()
                .filter(s -> s.id().equals("sidestep"))
                .findFirst()
                .orElseThrow();
        String rendered = SkillText.render(sidestep);
        assertTrue(rendered.contains("exactly 1 square(s)"), "Expected rendered text to substitute distance=1: " + rendered);
    }

    @Test
    @DisplayName("Sparse override file replaces specified keys and inherits defaults")
    void sparseOverrideFile(@TempDir Path tempDir) throws IOException {
        Path override = tempDir.resolve("custom-balance.properties");
        String content = """
                mana.cap=7
                mana.initial=4
                turn.limit=120
                score.target=10
                skill.sidestep.distance=3
                skill.sidestep.description=Move friendly piece {distance} steps.
                """;
        Files.writeString(override, content, StandardCharsets.UTF_8);

        RulesConfig config = RulesConfigLoader.load(override);
        assertEquals(7, config.manaCap());
        assertEquals(4, config.initialMana());
        assertEquals(120, config.turnLimit());
        assertEquals(10, config.scoreToWin());
        assertEquals(1, config.manaPerTurn()); // inherited from default

        SkillDefinition sidestep = config.skills().stream()
                .filter(s -> s.id().equals("sidestep"))
                .findFirst()
                .orElseThrow();
        assertEquals(3, sidestep.parameters().get(SkillParameter.DISTANCE));
        assertEquals("Move friendly piece 3 steps.", SkillText.render(sidestep));
    }

    @Test
    @DisplayName("Reject duplicate keys in override file")
    void rejectDuplicateKeys(@TempDir Path tempDir) throws IOException {
        Path override = tempDir.resolve("duplicate.properties");
        String content = """
                turn.limit=100
                turn.limit=120
                """;
        Files.writeString(override, content, StandardCharsets.UTF_8);

        ConfigException ex = assertThrows(ConfigException.class, () -> RulesConfigLoader.load(override));
        assertEquals("turn.limit", ex.key());
        assertTrue(ex.reason().contains("duplicate key"));
    }

    @Test
    @DisplayName("Reject unknown configuration keys in override file")
    void rejectUnknownKeys(@TempDir Path tempDir) throws IOException {
        Path override = tempDir.resolve("unknown.properties");
        String content = "unknown.custom.key=42\n";
        Files.writeString(override, content, StandardCharsets.UTF_8);

        ConfigException ex = assertThrows(ConfigException.class, () -> RulesConfigLoader.load(override));
        assertEquals("unknown.custom.key", ex.key());
        assertTrue(ex.reason().contains("unknown configuration key"));
    }

    @Test
    @DisplayName("Reject malformed numbers and out-of-bounds values in override file")
    void rejectMalformedAndOutOfBounds(@TempDir Path tempDir) throws IOException {
        Path badNum = tempDir.resolve("badnum.properties");
        Files.writeString(badNum, "turn.limit=not-a-number\n", StandardCharsets.UTF_8);
        assertThrows(ConfigException.class, () -> RulesConfigLoader.load(badNum));

        Path badLimit = tempDir.resolve("badlimit.properties");
        Files.writeString(badLimit, "turn.limit=1\n", StandardCharsets.UTF_8); // limit must be >= 2
        assertThrows(ConfigException.class, () -> RulesConfigLoader.load(badLimit));

        Path badDist = tempDir.resolve("baddist.properties");
        Files.writeString(badDist, "skill.sidestep.distance=7\n", StandardCharsets.UTF_8); // distance 1..6
        assertThrows(ConfigException.class, () -> RulesConfigLoader.load(badDist));
    }

    @Test
    @DisplayName("Reject invalid artwork paths with leading slash or path traversal")
    void rejectInvalidArtworkPaths(@TempDir Path tempDir) throws IOException {
        Path leadingSlash = tempDir.resolve("leadingslash.properties");
        Files.writeString(leadingSlash, "skill.sidestep.artwork=/assets/skills/sidestep.png\n", StandardCharsets.UTF_8);
        assertThrows(ConfigException.class, () -> RulesConfigLoader.load(leadingSlash));

        Path pathTraversal = tempDir.resolve("traversal.properties");
        Files.writeString(pathTraversal, "skill.sidestep.artwork=assets/../skills/sidestep.png\n", StandardCharsets.UTF_8);
        assertThrows(ConfigException.class, () -> RulesConfigLoader.load(pathTraversal));
    }

    @Test
    @DisplayName("Reject unresolved or unknown placeholders in skill descriptions")
    void rejectInvalidPlaceholders(@TempDir Path tempDir) throws IOException {
        Path unknownPlaceholder = tempDir.resolve("unknownph.properties");
        Files.writeString(unknownPlaceholder, "skill.sidestep.description=Test {unknown}\n", StandardCharsets.UTF_8);
        assertThrows(ConfigException.class, () -> RulesConfigLoader.load(unknownPlaceholder));

        Path unsuppliedPlaceholder = tempDir.resolve("unsuppliedph.properties");
        // Strike has no parameters, so {distance} is unresolved
        Files.writeString(unsuppliedPlaceholder, "skill.strike.description=Strike at {distance}\n", StandardCharsets.UTF_8);
        assertThrows(ConfigException.class, () -> RulesConfigLoader.load(unsuppliedPlaceholder));
    }

    @Test
    @DisplayName("Reject configuration when enabled skill copies cannot satisfy starting hand sizes")
    void rejectInsufficientCopies(@TempDir Path tempDir) throws IOException {
        Path sparseCopies = tempDir.resolve("sparsecopies.properties");
        // Set skill.handSize=6 (needs 12 copies), but make copies=0 for 20 skills (leaving only 4 copies)
        StringBuilder sb = new StringBuilder("skill.handSize=6\n");
        for (int i = 0; i < 20; i++) {
            sb.append("skill.").append(RulesConfig.CANONICAL_SKILL_IDS.get(i)).append(".copies=0\n");
        }
        Files.writeString(sparseCopies, sb.toString(), StandardCharsets.UTF_8);
        assertThrows(ConfigException.class, () -> RulesConfigLoader.load(sparseCopies));
    }
    @Test void invalidScoreTargetsAreRejected(@TempDir Path directory) throws IOException {
        Path override = directory.resolve("score.properties");
        for (String target : new String[] {"0", "-1", "101", "not-a-number"}) {
            Files.writeString(override, "score.target=" + target);
            assertThrows(ConfigException.class, () -> RulesConfigLoader.load(override));
        }
    }

}
