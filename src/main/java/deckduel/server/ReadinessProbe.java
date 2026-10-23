package deckduel.server;

import deckduel.net.ErrorMessage;
import deckduel.net.Message;
import deckduel.net.ProtocolHello;
import deckduel.net.ProtocolReady;
import deckduel.net.UpdateRequired;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/**
 * {@code probe --host H --port P [--trust-cert ca.pem] [--connect-address 127.0.0.1]}: checks that a server is ready for players
 * without logging in. It verifies the TLS certificate and host name exactly as the game does
 * (the Java runtime's trusted authorities unless a test authority is given), completes the protocol
 * greeting and prints what the server answered. No account details are sent.
 */
final class ReadinessProbe {
    private ReadinessProbe() { }

    static int run(String host, int port, Path trustCertificate, boolean tls) throws Exception {
        return run(host, port, trustCertificate, tls, null);
    }

    /** Dials loopback for a pre-firewall check while retaining the production TLS identity. */
    static int run(String host, int port, Path trustCertificate, boolean tls, String connectAddress) throws Exception {
        if (connectAddress != null && !java.net.InetAddress.getByName(connectAddress).isLoopbackAddress())
            throw new IOException("--connect-address must be loopback");
        SSLContext context = trustCertificate == null ? SSLContext.getDefault() : trusting(trustCertificate);
        try (var transport = new java.net.Socket()) {
            transport.connect(new InetSocketAddress(connectAddress == null ? host : connectAddress, port), 10_000);
            // The layered socket verifies host, never the loopback dial address.
            try (var socket = tls ? context.getSocketFactory().createSocket(transport, host, port, true) : transport) {
                socket.setSoTimeout(10_000);
                if (socket instanceof SSLSocket secure) {
                    SSLParameters parameters = secure.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    if (!host.matches("[0-9.]+|\\[?[0-9a-fA-F]*:[0-9a-fA-F:]+]?")) parameters.setServerNames(List.of(new SNIHostName(host)));
                    secure.setSSLParameters(parameters);
                    secure.startHandshake();
                    X509Certificate leaf = (X509Certificate) secure.getSession().getPeerCertificates()[0];
                    System.out.println("tls ok " + secure.getSession().getProtocol() + " certificate " + leaf.getSubjectX500Principal().getName()
                            + " expires " + leaf.getNotAfter().toInstant());
                }
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                in.setObjectInputFilter(Message.WIRE_FILTER);
                Object greeting = in.readObject();
                if (!(greeting instanceof ErrorMessage banner) || !Message.GREETING.equals(banner.text())) {
                    System.out.println("error unexpected greeting " + greeting);
                    return 1;
                }
                out.writeObject(new ProtocolHello(Message.PROTOCOL_VERSION, ServerMain.version()));
                out.flush();
                Object reply = in.readObject();
                if (reply instanceof ProtocolReady ready) {
                    System.out.println("ok protocol " + ready.version() + " ready at " + host + ":" + port);
                    return 0;
                }
                if (reply instanceof UpdateRequired required) {
                    System.out.println("ok protocol " + Message.PROTOCOL_VERSION + " ready; clients need " + required.minimumVersion() + " or newer");
                    return 0;
                }
                System.out.println("error " + reply);
                return 1;
            }
        }
    }

    private static SSLContext trusting(Path pem) throws Exception {
        try (InputStream in = Files.newInputStream(pem)) {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int index = 0;
            for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(in))
                store.setCertificateEntry("trusted-" + index++, certificate);
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), null);
            return context;
        }
    }
}
