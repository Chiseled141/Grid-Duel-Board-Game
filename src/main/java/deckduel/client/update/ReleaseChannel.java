package deckduel.client.update;

import deckduel.client.startup.StartupServices;
import deckduel.client.startup.UpdateInstaller;
import deckduel.client.startup.UpdateService;
import deckduel.launcher.InstallLayout;
import deckduel.launcher.PlatformId;
import java.io.IOException;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Builds the client's real update services from the packaged release configuration. */
public final class ReleaseChannel {
    private static final Logger LOG = Logger.getLogger(ReleaseChannel.class.getName());
    private static final int DOWNLOAD_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 2_000;

    private ReleaseChannel() { }

    /**
     * Real services when a release channel is packaged; the honest not-configured services when
     * none is; and, when the packaged configuration is broken, a check that reports why.
     *
     * @param layout the installation that started this client, or null when run directly from a jar
     */
    public static StartupServices services(InstallLayout layout, int protocol) {
        Optional<UpdateConfig> config;
        try {
            config = UpdateConfig.packaged();
        } catch (IllegalStateException broken) {
            LOG.log(Level.WARNING, "invalid update configuration", broken);
            return new StartupServices(installed -> { throw new IOException(broken.getMessage()); },
                    UpdateInstaller.notConfigured(), UnaryOperator.identity(), null, null);
        }
        if (config.isEmpty()) return StartupServices.unconfigured();
        String platform = PlatformId.current();
        UpdateService updates = new HttpUpdateService(config.get(), protocol, platform);
        UpdateInstaller installer = new StagedInstaller(layout, platform, new PackageDownloader(DOWNLOAD_ATTEMPTS, RETRY_DELAY_MILLIS));
        return new StartupServices(updates, installer, UnaryOperator.identity(),
                layout == null ? null : new HelperRestarter(layout), null);
    }
}
