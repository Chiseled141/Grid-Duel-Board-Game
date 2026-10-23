package deckduel.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Which certificate authorities the client trusts. Players use the Java runtime's own trusted
 * roots, which include public authorities such as Let's Encrypt; a development override may
 * trust one extra local test authority from a PEM file. Nothing here ever trusts everything.
 */
public final class ClientTls {
    private ClientTls() { }

    /** The Java runtime's default trust (its cacerts file), as used for production servers. */
    public static SSLContext systemTrust() {
        try {
            return SSLContext.getDefault();
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("This Java runtime has no TLS support", impossible);
        }
    }

    /** Trusts only the authorities in a PEM file, for local test servers. */
    public static SSLContext trusting(Path pemCertificates) throws IOException {
        try (InputStream in = Files.newInputStream(pemCertificates)) {
            Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509").generateCertificates(in);
            if (certificates.isEmpty()) throw new IOException("No certificates in " + pemCertificates);
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) {
                if (!(certificate instanceof X509Certificate)) throw new IOException("Not an X.509 certificate in " + pemCertificates);
                store.setCertificateEntry("trusted-" + index++, certificate);
            }
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException invalid) {
            throw new IOException("Cannot read trusted certificates from " + pemCertificates, invalid);
        }
    }
}
