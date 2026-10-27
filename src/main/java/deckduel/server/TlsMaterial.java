package deckduel.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

/**
 * The server's certificate chain and private key, read from PEM files such as certbot's
 * {@code fullchain.pem} and {@code privkey.pem} (PKCS#8, RSA or EC). {@link #reload()} re-reads
 * them for new connections, so a renewed certificate is used without restarting the server or
 * ending matches; a file that fails to load keeps the previous certificate in service.
 */
public final class TlsMaterial {
    private static final Logger LOG = Logger.getLogger(TlsMaterial.class.getName());

    private final Path chainFile, keyFile;
    private final SSLContext context;
    private final SwappableKeyManager keys = new SwappableKeyManager();
    private volatile X509Certificate leaf;

    private TlsMaterial(Path chainFile, Path keyFile) throws IOException {
        this.chainFile = chainFile;
        this.keyFile = keyFile;
        reload();
        try {
            context = SSLContext.getInstance("TLS");
            context.init(new javax.net.ssl.KeyManager[] {keys}, null, null);
        } catch (GeneralSecurityException impossible) {
            throw new IOException("TLS is unavailable in this Java runtime", impossible);
        }
    }

    /** Loads a PEM certificate chain (leaf first) and its PKCS#8 private key. */
    public static TlsMaterial load(Path chainFile, Path keyFile) throws IOException { return new TlsMaterial(chainFile, keyFile); }

    /** The context new server sockets are created from. */
    public SSLContext context() { return context; }

    /** The certificate currently presented. */
    public X509Certificate certificate() { return leaf; }

    /** Re-reads both files; on failure the current certificate stays in use and the error is thrown. */
    public synchronized void reload() throws IOException {
        List<X509Certificate> chain = readChain(chainFile);
        PrivateKey key = readKey(keyFile);
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            char[] none = new char[0];
            store.setKeyEntry("server", key, none, chain.toArray(new Certificate[0]));
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(store, none);
            keys.delegate = (X509ExtendedKeyManager) factory.getKeyManagers()[0];
            leaf = chain.get(0);
        } catch (GeneralSecurityException mismatch) {
            throw new IOException("The private key in " + keyFile + " does not fit the certificate in " + chainFile, mismatch);
        }
        LOG.info(() -> "TLS certificate for " + leaf.getSubjectX500Principal().getName() + ", valid until " + leaf.getNotAfter());
    }

    private static List<X509Certificate> readChain(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            List<X509Certificate> chain = CertificateFactory.getInstance("X.509").generateCertificates(in).stream()
                    .map(X509Certificate.class::cast).toList();
            if (chain.isEmpty()) throw new IOException("No certificate in " + file);
            return chain;
        } catch (java.security.cert.CertificateException invalid) {
            throw new IOException("Cannot read the certificate chain " + file, invalid);
        }
    }

    private static PrivateKey readKey(Path file) throws IOException {
        String pem = Files.readString(file);
        if (!pem.contains("-----BEGIN PRIVATE KEY-----"))
            throw new IOException(file + " is not a PKCS#8 PEM private key (BEGIN PRIVATE KEY); convert it with: openssl pkcs8 -topk8 -nocrypt");
        String base64 = pem.substring(pem.indexOf("-----BEGIN PRIVATE KEY-----") + 27, pem.indexOf("-----END PRIVATE KEY-----"))
                .replaceAll("\\s", "");
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64));
        for (String algorithm : List.of("EC", "RSA")) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(spec);
            } catch (GeneralSecurityException notThisOne) {
                // Try the next algorithm.
            }
        }
        throw new IOException("Unsupported private key in " + file + " (expected EC or RSA)");
    }

    /** Hands each new handshake the current key manager. */
    private static final class SwappableKeyManager extends X509ExtendedKeyManager {
        volatile X509ExtendedKeyManager delegate;

        @Override public String[] getClientAliases(String type, Principal[] issuers) { return delegate.getClientAliases(type, issuers); }
        @Override public String chooseClientAlias(String[] type, Principal[] issuers, Socket socket) { return delegate.chooseClientAlias(type, issuers, socket); }
        @Override public String[] getServerAliases(String type, Principal[] issuers) { return delegate.getServerAliases(type, issuers); }
        @Override public String chooseServerAlias(String type, Principal[] issuers, Socket socket) { return delegate.chooseServerAlias(type, issuers, socket); }
        @Override public String chooseEngineServerAlias(String type, Principal[] issuers, SSLEngine engine) { return delegate.chooseEngineServerAlias(type, issuers, engine); }
        @Override public X509Certificate[] getCertificateChain(String alias) { return delegate.getCertificateChain(alias); }
        @Override public PrivateKey getPrivateKey(String alias) { return delegate.getPrivateKey(alias); }
    }
}
