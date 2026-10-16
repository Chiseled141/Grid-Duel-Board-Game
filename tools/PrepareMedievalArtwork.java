import javax.imageio.ImageIO;
import java.nio.file.*;

/** Extracts the twenty-four uniformly registered woodcut illustrations from the authored atlas. */
public final class PrepareMedievalArtwork {
    /** Reads the supplied 6-by-4 atlas and writes the runtime skill illustration files. */
    public static void main(String[] args) throws Exception {
        String[] ids = {"sidestep", "dash", "blink", "exchange", "advance", "recall",
                "strike", "pierce", "surround", "sweep", "shove", "pull",
                "vault", "flank", "transposition", "gust", "rally", "scatter",
                "crossfire", "chain-strike", "mana-surge", "refocus", "foresight", "banish"};
        var atlas = ImageIO.read(Path.of(args[0]).toFile());
        if (atlas.getWidth() % 6 != 0 || atlas.getHeight() % 4 != 0)
            throw new IllegalArgumentException("Atlas must have six equal columns and four equal rows");
        int width = atlas.getWidth() / 6, height = atlas.getHeight() / 4;
        Path output = Path.of(args[1]);
        Files.createDirectories(output);
        for (int index = 0; index < ids.length; index++) {
            var tile = atlas.getSubimage(index % 6 * width, index / 6 * height, width, height);
            ImageIO.write(tile, "png", output.resolve(ids[index] + ".png").toFile());
        }
    }
}
