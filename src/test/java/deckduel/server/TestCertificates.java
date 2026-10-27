package deckduel.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Throwaway local certificates for TLS tests, made with the JDK's {@code keytool}: a test
 * authority and server certificates signed by it, written as PEM files like certbot's
 * {@code fullchain.pem} and {@code privkey.pem}. These are test material only, never production.
 */
public final class TestCertificates {
    /** Keystore password for the throwaway test keystores, passed to keytool through its environment. */
    private static final String STORE_PASSWORD = "test-only-password";

    /** One issued server certificate: its chain (leaf then authority) and PKCS#8 key. */
    public record Issued(Path chain, Path key) { }

    private final Path folder;
    private final String authority;

    /** Creates a fresh test authority named {@code name} in {@code folder}. */
    public TestCertificates(Path folder, String name) throws IOException, InterruptedException {
        this.folder = folder;
        this.authority = name;
        Files.createDirectories(folder);
        keytool("-genkeypair", "-alias", "ca", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=" + name,
                "-ext", "bc:c", "-validity", "30", "-keystore", store(name));
        keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", store(name), "-file", authorityPem().toString());
    }

    /** The authority's certificate as PEM, for a client's trust override. */
    public Path authorityPem() { return folder.resolve(authority + "-ca.pem"); }

    /**
     * Issues a server certificate for {@code names} ("dns:localhost,ip:127.0.0.1"), valid for
     * {@code days} from {@code startOffset} ("-30d" for one that has expired, "" for today).
     */
    public Issued issue(String label, String names, String startOffset, int days) throws IOException, InterruptedException {
        String keystore = store(label);
        Path csr = folder.resolve(label + ".csr"), leaf = folder.resolve(label + ".pem");
        keytool("-genkeypair", "-alias", "server", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=" + label,
                "-validity", "30", "-keystore", keystore);
        keytool("-certreq", "-alias", "server", "-keystore", keystore, "-file", csr.toString());
        List<String> sign = new ArrayList<>(List.of("-gencert", "-alias", "ca", "-keystore", store(authority), "-infile", csr.toString(),
                "-outfile", leaf.toString(), "-rfc", "-ext", "san=" + names, "-ext", "eku=serverAuth", "-validity", String.valueOf(days)));
        if (!startOffset.isEmpty()) { sign.add("-startdate"); sign.add(startOffset); }
        keytool(sign.toArray(new String[0]));
        Path chain = folder.resolve(label + "-fullchain.pem");
        Files.writeString(chain, Files.readString(leaf) + Files.readString(authorityPem()));
        Path key = folder.resolve(label + "-privkey.pem");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey(keystore).getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        return new Issued(chain, key);
    }

    private PrivateKey privateKey(String keystore) throws IOException {
        try (InputStream in = Files.newInputStream(Path.of(keystore))) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(in, STORE_PASSWORD.toCharArray());
            return (PrivateKey) store.getKey("server", STORE_PASSWORD.toCharArray());
        } catch (java.security.GeneralSecurityException failure) {
            throw new IOException(failure);
        }
    }

    private String store(String name) { return folder.resolve(name + ".p12").toString(); }

    private void keytool(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "keytool").toString()));
        command.addAll(List.of(args));
        command.addAll(List.of("-storetype", "PKCS12", "-storepass:env", "DD_TEST_STORE", "-noprompt"));
        if (!args[0].equals("-exportcert")) command.addAll(List.of("-keypass:env", "DD_TEST_STORE"));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("DD_TEST_STORE", STORE_PASSWORD);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0)
            throw new IOException("keytool " + String.join(" ", args) + " failed: " + output);
    }
}
