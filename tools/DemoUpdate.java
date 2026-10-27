import deckduel.client.startup.InstalledVersion;
import deckduel.client.startup.StartupServices;
import deckduel.client.startup.UpdateCheck;
import deckduel.client.update.ReleaseChannel;
import deckduel.launcher.InstallLayout;
import deckduel.net.Message;
import java.nio.file.Path;

/**
 * Does what the startup screen's Update button does, without the window, for tools/update-demo.sh:
 * check the packaged release channel, download and verify the offered package, hand over to the
 * helper, and exit so the helper can activate and restart. Run with the installed game jar on the
 * class path: java -cp <install>/versions/<v>/deck-duel.jar tools/DemoUpdate.java <install>
 */
public class DemoUpdate {
    public static void main(String[] args) throws Exception {
        InstallLayout layout = new InstallLayout(Path.of(args[0]));
        StartupServices services = ReleaseChannel.services(layout, Message.PROTOCOL_VERSION);
        String installed = InstalledVersion.current();
        UpdateCheck check = services.updates().check(installed);
        System.out.println("Installed " + installed + "; update check: " + check.status() + " " + check.latestVersion());
        if (!check.available()) System.exit(1);
        int[] shown = {-1};
        services.installer().install(check, fraction -> {
            int percent = (int) Math.round(fraction * 100);
            if (percent / 10 != shown[0]) { shown[0] = percent / 10; System.out.println("  " + percent + "%"); }
        });
        System.out.println("Downloaded and verified " + check.latestVersion() + "; handing over to the helper and closing.");
        services.restarter().handOver(check.latestVersion());
        System.exit(0);
    }
}
