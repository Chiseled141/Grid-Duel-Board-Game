package deckduel.client.update;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import deckduel.client.startup.ReleasePackage;
import deckduel.client.startup.UpdateCheck;
import deckduel.launcher.Hashes;
import deckduel.launcher.InstallLayout;
import deckduel.launcher.PlatformId;
import deckduel.launcher.TestPackages;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The client side of a real update against a local HTTP server standing in for an S3 bucket:
 * signed metadata, version decisions, resumable verified downloads and staging into an install.
 */
class UpdateChannelTest {
    @TempDir Path temporary;
    private HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final List<String> ranges = new CopyOnWriteArrayList<>();
    // Requests for a path that should be cut off after this many bytes, once.
    private final Map<String, Integer> truncateOnce = new ConcurrentHashMap<>();
    private volatile boolean honourRange = true;
    private KeyPair key;
    private String platform;

    @BeforeEach void serve() throws Exception {
        key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        platform = PlatformId.current();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    private void answer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] body = files.get(path);
        if (body == null) { exchange.sendResponseHeaders(404, -1); exchange.close(); return; }
        String range = exchange.getRequestHeaders().getFirst("Range");
        int from = 0;
        if (range != null) {
            ranges.add(path + " " + range);
            if (honourRange) from = Integer.parseInt(range.substring("bytes=".length(), range.indexOf('-')));
        }
        Integer cut = truncateOnce.remove(path);
        int length = body.length - from;
        exchange.sendResponseHeaders(from > 0 ? 206 : 200, length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body, from, cut == null ? length : Math.min(cut, length));
        }
    }

    private URI url(String path) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path); }

    private String metadata(String version, String minimum, int protocol, String packagePlatform, byte[] pkg) {
        return "format=1\nversion=" + version + "\nminimumVersion=" + minimum + "\nminimumProtocol=" + protocol + "\n"
                + (pkg == null ? "" : "package." + packagePlatform + ".url=pkg.zip\npackage." + packagePlatform + ".size=" + pkg.length
                + "\npackage." + packagePlatform + ".sha256=" + sha256(pkg) + "\n");
    }

    private void publish(String body, KeyPair signer) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        files.put("/channel/latest.properties", bytes);
        files.put("/channel/latest.properties.sig", sign(bytes, signer).getBytes(StandardCharsets.US_ASCII));
    }

    private static String sign(byte[] body, KeyPair signer) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(signer.getPrivate());
        signature.update(body);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private static String sha256(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(Hashes.sha256().digest(bytes));
    }

    private HttpUpdateService service() {
        return new HttpUpdateService(new UpdateConfig(url("/channel/latest.properties"), key.getPublic()), 15, platform);
    }

    @Test void signedMetadataDecidesRequiredOptionalOrCurrentAndResolvesThePackage() throws Exception {
        byte[] pkg = "package bytes".getBytes(StandardCharsets.UTF_8);
        publish(metadata("1.2.0", "1.0.0", 15, platform, pkg), key);
        UpdateCheck optional = service().check("1.1.0");
        assertEquals(UpdateCheck.Status.OPTIONAL, optional.status());
        assertEquals(url("/channel/pkg.zip"), optional.release().url(), "relative package addresses follow the metadata");
        assertEquals(pkg.length, optional.release().size());
        assertEquals(UpdateCheck.Status.REQUIRED, service().check("0.9.0").status(), "older than the minimum version");
        assertEquals(UpdateCheck.Status.UP_TO_DATE, service().check("1.2.0").status());
        assertEquals(UpdateCheck.Status.UP_TO_DATE, service().check("1.3.0").status(), "never a downgrade");
        assertEquals(UpdateCheck.Status.OPTIONAL, service().check("1.2.0-rc.1").status(), "a release candidate is older than its release");
        publish(metadata("1.2.0", "1.0.0", 16, platform, pkg), key);
        assertEquals(UpdateCheck.Status.REQUIRED, service().check("1.1.0").status(), "the client's protocol is too old");
        IOException development = assertThrows(IOException.class, () -> service().check("development build"));
        assertTrue(development.getMessage().contains("not a release"));
    }

    @Test void tamperedOrWronglySignedMetadataIsRejectedBeforeUse() throws Exception {
        byte[] pkg = new byte[] {1, 2, 3};
        publish(metadata("9.0.0", "9.0.0", 15, platform, pkg), KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        assertTrue(assertThrows(IOException.class, () -> service().check("1.0.0")).getMessage().contains("not signed"));
        publish(metadata("1.2.0", "1.0.0", 15, platform, pkg), key);
        byte[] tampered = new String(files.get("/channel/latest.properties"), StandardCharsets.UTF_8)
                .replace("minimumVersion=1.0.0", "minimumVersion=1.2.0").getBytes(StandardCharsets.UTF_8);
        files.put("/channel/latest.properties", tampered);
        assertThrows(IOException.class, () -> service().check("1.1.0"), "a changed byte breaks the signature");
        files.put("/channel/latest.properties.sig", "not base64!".getBytes(StandardCharsets.US_ASCII));
        assertTrue(assertThrows(IOException.class, () -> service().check("1.1.0")).getMessage().contains("invalid signature"));
        files.remove("/channel/latest.properties.sig");
        assertTrue(assertThrows(IOException.class, () -> service().check("1.1.0")).getMessage().contains("not found"));
        publish("format=1\nversion=1.2.0\n", key);
        assertTrue(assertThrows(IOException.class, () -> service().check("1.1.0")).getMessage().contains("malformed"));
        files.put("/channel/latest.properties", new byte[HttpUpdateService.MAX_METADATA_BYTES + 1]);
        assertTrue(assertThrows(IOException.class, () -> service().check("1.1.0")).getMessage().contains("large"));
    }

    @Test void aReleaseWithoutThisPlatformIsOfferedButCannotBeInstalled() throws Exception {
        String other = platform.startsWith("windows") ? "linux-x64" : "windows-x64";
        publish(metadata("1.2.0", "1.0.0", 15, other, new byte[] {1}), key);
        UpdateCheck check = service().check("1.1.0");
        assertEquals(UpdateCheck.Status.OPTIONAL, check.status());
        assertNull(check.release());
        var installer = new StagedInstaller(new InstallLayout(temporary.resolve("install")), platform, new PackageDownloader(1, 0));
        IOException failure = assertThrows(IOException.class, () -> installer.install(check, p -> { }));
        assertTrue(failure.getMessage().contains("no package for this computer"));
    }

    @Test void onlyHttpsIsAcceptedExceptOnThisComputer() {
        assertThrows(IllegalStateException.class, () -> UpdateConfig.checkedUrl(URI.create("http://updates.example.com/latest.properties")));
        assertThrows(IllegalStateException.class, () -> UpdateConfig.checkedUrl(URI.create("file:///tmp/latest.properties")));
        UpdateConfig.checkedUrl(URI.create("https://bucket.s3.eu-west-1.amazonaws.com/stable/latest.properties"));
        UpdateConfig.checkedUrl(URI.create("http://127.0.0.1:8080/latest.properties"));
        var empty = new Properties();
        empty.setProperty("metadataUrl", "${deckduel.update.url}");
        empty.setProperty("publicKey", "");
        assertTrue(UpdateConfig.from(empty).isEmpty(), "an unfiltered build is honestly not configured");
        var half = new Properties();
        half.setProperty("metadataUrl", "https://example.com/latest.properties");
        assertThrows(IllegalStateException.class, () -> UpdateConfig.from(half));
        var badKey = new Properties();
        badKey.setProperty("metadataUrl", "https://example.com/latest.properties");
        badKey.setProperty("publicKey", "AAAA");
        assertThrows(IllegalStateException.class, () -> UpdateConfig.from(badKey));
        assertTrue(UpdateConfig.packaged().isEmpty(), "this build ships no release channel");
    }

    private ReleasePackage hosted(byte[] bytes) {
        files.put("/channel/pkg.zip", bytes);
        return new ReleasePackage("1.2.0", platform, url("/channel/pkg.zip"), bytes.length, sha256(bytes));
    }

    @Test void anInterruptedDownloadResumesWhereItStoppedAndReportsProgress() throws Exception {
        byte[] bytes = new byte[300_000];
        new java.util.Random(1).nextBytes(bytes);
        ReleasePackage release = hosted(bytes);
        truncateOnce.put("/channel/pkg.zip", 120_000);
        List<Double> progress = new ArrayList<>();
        Path done = new PackageDownloader(3, 0).download(release, temporary.resolve("staging"), progress::add);
        assertArrayEquals(bytes, Files.readAllBytes(done));
        assertEquals(List.of("/channel/pkg.zip bytes=120000-"), ranges, "the second attempt asked only for the rest");
        assertEquals(1.0, progress.get(progress.size() - 1));
        assertFalse(Files.exists(done.resolveSibling(done.getFileName() + ".part")));
        // A verified package is reused without downloading again.
        files.remove("/channel/pkg.zip");
        assertEquals(done, new PackageDownloader(1, 0).download(release, temporary.resolve("staging"), p -> { }));
    }

    @Test void aServerIgnoringRangeSendsEverythingAgainAndStillVerifies() throws Exception {
        byte[] bytes = new byte[200_000];
        new java.util.Random(2).nextBytes(bytes);
        ReleasePackage release = hosted(bytes);
        honourRange = false;
        truncateOnce.put("/channel/pkg.zip", 50_000);
        Path done = new PackageDownloader(3, 0).download(release, temporary.resolve("staging"), p -> { });
        assertArrayEquals(bytes, Files.readAllBytes(done));
    }

    @Test void corruptTruncatedOrOversizedDownloadsAreDiscarded() throws Exception {
        byte[] bytes = new byte[100_000];
        new java.util.Random(3).nextBytes(bytes);
        ReleasePackage release = hosted(bytes);
        byte[] corrupt = bytes.clone();
        corrupt[500] ^= 1;
        files.put("/channel/pkg.zip", corrupt);
        Path staging = temporary.resolve("staging");
        IOException mismatch = assertThrows(IOException.class, () -> new PackageDownloader(2, 0).download(release, staging, p -> { }));
        assertTrue(mismatch.getMessage().contains("checksum"));
        try (var left = Files.list(staging)) { assertEquals(0, left.count(), "a wrong file is never kept"); }

        files.put("/channel/pkg.zip", java.util.Arrays.copyOf(bytes, 60_000));
        IOException early = assertThrows(IOException.class, () -> new PackageDownloader(2, 0).download(release, staging, p -> { }));
        assertTrue(early.getMessage().contains("stopped early"), early.getMessage());

        files.put("/channel/pkg.zip", java.util.Arrays.copyOf(bytes, 150_000));
        Files.deleteIfExists(staging.resolve("deck-duel-1.2.0-" + platform + ".zip.part"));
        IOException tooMuch = assertThrows(IOException.class, () -> new PackageDownloader(1, 0).download(release, staging, p -> { }));
        assertTrue(tooMuch.getMessage().contains("more data"), tooMuch.getMessage());

        files.remove("/channel/pkg.zip");
        IOException missing = assertThrows(IOException.class, () -> new PackageDownloader(1, 0).download(release, staging, p -> { }));
        assertTrue(missing.getMessage().contains("not found"));
        ReleasePackage huge = new ReleasePackage("1.2.0", platform, release.url(), PackageDownloader.MAX_PACKAGE_BYTES + 1, release.sha256());
        assertThrows(IOException.class, () -> new PackageDownloader(1, 0).download(huge, staging, p -> { }));
    }

    @Test void theInstallerStagesAVerifiedVersionWithoutActivatingIt() throws Exception {
        var packages = new TestPackages(temporary.resolve("build"));
        InstallLayout layout = new InstallLayout(temporary.resolve("install"));
        layout.activate(layout.installPackage(packages.packageFor("1.1.0")).version());
        byte[] pkg = Files.readAllBytes(packages.packageFor("1.2.0"));
        files.put("/channel/pkg.zip", pkg);
        publish(metadata("1.2.0", "1.0.0", 15, platform, pkg), key);
        UpdateCheck check = service().check("1.1.0");
        List<Double> progress = new ArrayList<>();
        new StagedInstaller(layout, platform, new PackageDownloader(2, 0)).install(check, progress::add);
        layout.verify("1.2.0");
        assertEquals("1.1.0", layout.pointer().orElseThrow().active(), "activation waits for the helper");
        assertEquals(1.0, progress.get(progress.size() - 1));
        try (var staged = Files.list(layout.staging())) { assertEquals(0, staged.count(), "the download is removed once unpacked"); }

        var notInstalled = new StagedInstaller(null, platform, new PackageDownloader(1, 0));
        assertTrue(assertThrows(IOException.class, () -> notInstalled.install(check, p -> { }))
                .getMessage().contains("not started from an installation"));
    }
}
