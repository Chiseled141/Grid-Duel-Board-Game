package deckduel.client.update;

import deckduel.client.startup.UpdateCheck;
import deckduel.client.startup.UpdateInstaller;
import deckduel.launcher.InstallLayout;
import deckduel.launcher.PackageManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;

/**
 * Prepares an offered release while the game keeps running: downloads and verifies the package
 * into the installation's staging folder, then unpacks it into a new {@code versions/<version>}
 * folder and checks every file. Nothing is activated here; the helper does that after the game
 * closes. Progress is 0–0.9 for the download and the rest for unpacking.
 */
public final class StagedInstaller implements UpdateInstaller {
    private final InstallLayout layout;
    private final String platform;
    private final PackageDownloader downloader;

    /**
     * @param layout the running installation, or null when this copy was not started by the launcher
     */
    public StagedInstaller(InstallLayout layout, String platform, PackageDownloader downloader) {
        this.layout = layout;
        this.platform = platform;
        this.downloader = downloader;
    }

    @Override public void install(UpdateCheck update, DoubleConsumer progress) throws IOException {
        if (layout == null)
            throw new IOException("This copy of Deck Duel was not started from an installation, so it cannot update itself.");
        if (update.release() == null)
            throw new IOException("Version " + update.latestVersion() + " has no package for this computer (" + platform + ") yet.");
        if (!update.release().platform().equals(platform))
            throw new IOException("The offered package is for " + update.release().platform() + ", not " + platform + ".");
        Path zip = downloader.download(update.release(), layout.staging(), fraction -> progress.accept(fraction * .9));
        PackageManifest manifest = layout.installPackage(zip);
        if (!manifest.version().equals(update.latestVersion()) || !manifest.platform().equals(platform))
            throw new IOException("The package holds " + manifest.version() + " for " + manifest.platform() + ", not what was offered.");
        // The verified version folder replaces the download.
        Files.deleteIfExists(zip);
        progress.accept(1);
    }
}
