package deckduel.server;

import static org.junit.jupiter.api.Assertions.*;

import deckduel.client.ClientTls;
import deckduel.client.DeckDuelClient;
import deckduel.client.startup.ServerRoute;
import deckduel.client.startup.UpdateRequiredException;
import deckduel.db.Database;
import deckduel.db.SqliteUserDao;
import deckduel.net.LoginResponse;
import deckduel.net.Message;
import deckduel.net.RegisterRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real TLS between the client and server with throwaway local certificates: trusted connections
 * work; wrong host names, untrusted authorities and expired certificates are refused before any
 * credentials are sent; silent peers time out and are cleaned up; outdated clients are refused.
 */
class SecureConnectionTest {
    @TempDir static Path certificates;
    @TempDir Path temporary;
    private static TestCertificates authority, stranger;
    private static TestCertificates.Issued good, wrongHost, expired, renewed, foreign;
    private Database database;
    private GameServer server;

    @BeforeAll static void issue() throws Exception {
        authority = new TestCertificates(certificates.resolve("trusted"), "Deck Duel Test CA");
        good = authority.issue("localhost", "dns:localhost,ip:127.0.0.1", "", 30);
        wrongHost = authority.issue("other", "dns:other.example", "", 30);
        expired = authority.issue("expired", "dns:localhost,ip:127.0.0.1", "-30d", 1);
        renewed = authority.issue("renewed", "dns:localhost,ip:127.0.0.1", "", 60);
        stranger = new TestCertificates(certificates.resolve("stranger"), "Unknown CA");
        foreign = stranger.issue("foreign", "dns:localhost,ip:127.0.0.1", "", 30);
    }

    @AfterEach void stop() throws Exception {
        if (server != null) server.stop();
        if (database != null) database.close();
    }

    private int start(ServerConfig config) throws Exception {
        database = new Database(temporary.resolve("tls.db"));
        server = new GameServer(config, new SqliteUserDao(database));
        return server.start();
    }

    private int startTls(TestCertificates.Issued certificate) throws Exception {
        return start(ServerConfig.defaults(0).withTls(TlsMaterial.load(certificate.chain(), certificate.key())).withHandshakeTimeout(1_000));
    }

    private static DeckDuelClient client(SSLContext trust, LinkedBlockingQueue<Message> inbox, String version, int timeout) {
        return new DeckDuelClient(inbox::add, reason -> { }, trust, version, timeout);
    }

    private static SSLContext trustTestAuthority() throws IOException { return ClientTls.trusting(authority.authorityPem()); }

    @Test void readinessCanDialLoopbackWithoutResolvingTheCertificateHost() throws Exception {
        int port = startTls(wrongHost); // SAN is other.example, not localhost or 127.0.0.1.
        assertEquals(0, ServerMain.run(new String[] {"probe", "--host", "other.example", "--port", String.valueOf(port),
                "--trust-cert", authority.authorityPem().toString(), "--connect-address", "127.0.0.1"}));
        assertThrows(IOException.class, () -> ReadinessProbe.run("localhost", port, authority.authorityPem(), true, "127.0.0.1"),
                "loopback routing must not bypass certificate hostname verification");
        assertThrows(IOException.class, () -> ReadinessProbe.run("other.example", port, authority.authorityPem(), true, "203.0.113.1"),
                "the override is only for local readiness checks");
    }

    @Test void aTrustedCertificateCarriesLoginAndRegistration() throws Exception {
        int port = startTls(good);
        var inbox = new LinkedBlockingQueue<Message>();
        try (var client = client(trustTestAuthority(), inbox, "1.0.0", 5_000)) {
            client.ensureConnected(new ServerRoute("localhost", port, true));
            assertTrue(client.isConnected());
            client.send(new RegisterRequest("secure_alice", "secret123"));
            Message reply = inbox.poll(10, TimeUnit.SECONDS);
            assertTrue(reply instanceof LoginResponse response && response.ok(), String.valueOf(reply));
        }
    }

    private static IOException refused(SSLContext trust, int port, String host) {
        var client = client(trust, new LinkedBlockingQueue<>(), "1.0.0", 5_000);
        IOException failure = assertThrows(IOException.class, () -> client.ensureConnected(new ServerRoute(host, port, true)));
        assertFalse(client.isConnected(), "nothing is left connected, so nothing can be sent");
        client.close();
        return failure;
    }

    @Test void aCertificateForAnotherHostIsRefused() throws Exception {
        int port = startTls(wrongHost);
        assertTrue(refused(trustTestAuthority(), port, "localhost").getMessage().contains("different host name"));
    }

    @Test void anUntrustedAuthorityIsRefusedIncludingByTheJavaRuntimesOwnTrust() throws Exception {
        int port = startTls(foreign);
        assertTrue(refused(trustTestAuthority(), port, "localhost").getMessage().contains("not issued by a trusted authority"));
        server.stop();
        int again = startTls(good);
        assertTrue(refused(ClientTls.systemTrust(), again, "localhost").getMessage().contains("not issued by a trusted authority"),
                "players' default trust does not accept local test certificates");
    }

    @Test void anExpiredCertificateIsRefused() throws Exception {
        int port = startTls(expired);
        assertTrue(refused(trustTestAuthority(), port, "localhost").getMessage().contains("expired"));
    }

    @Test void plaintextAndTlsNeverMixSilently() throws Exception {
        int tlsPort = startTls(good);
        var plainClient = client(trustTestAuthority(), new LinkedBlockingQueue<>(), "1.0.0", 2_000);
        assertThrows(IOException.class, () -> plainClient.ensureConnected(new ServerRoute("127.0.0.1", tlsPort, false)));
        assertFalse(plainClient.isConnected());
        server.stop(); database.close();
        int plainPort = start(ServerConfig.defaults(0));
        var tlsClient = client(trustTestAuthority(), new LinkedBlockingQueue<>(), "1.0.0", 2_000);
        IOException failure = assertThrows(IOException.class, () -> tlsClient.ensureConnected(new ServerRoute("localhost", plainPort, true)));
        assertTrue(failure.getMessage().contains("secure connection"), failure.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new ServerRoute("play.example.com", 5555, false), "no plaintext to another computer");
        assertTrue(new ServerRoute("play.example.com", 5555).tls(), "remote routes default to TLS");
    }

    @Test void aServerNeverListensBeyondThisComputerWithoutTls() throws Exception {
        database = new Database(temporary.resolve("public.db"));
        var exposed = new GameServer(ServerConfig.defaults(0).withBind(InetAddress.getByName("0.0.0.0")), new SqliteUserDao(database));
        IOException refusal = assertThrows(IOException.class, exposed::start);
        assertTrue(refusal.getMessage().contains("without TLS"));
        exposed.stop();
    }

    @Test void aSilentPeerIsDroppedAfterTheHandshakeTimeout() throws Exception {
        int port = startTls(good);
        long started = System.nanoTime();
        try (Socket silent = new Socket("127.0.0.1", port)) {
            silent.setSoTimeout(10_000);
            InputStream in = silent.getInputStream();
            // The server never gets a ClientHello, gives up after its timeout, may send a TLS alert, and closes.
            int total = 0;
            try {
                for (int read; (read = in.read()) != -1 && total < 1024; ) total++;
            } catch (java.net.SocketException reset) {
                // Closed with a reset: also the end.
            }
            assertTrue(total < 64, "at most a short alert, then the end of the stream");
        }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 8_000, "bounded by the handshake timeout");
        long deadline = System.currentTimeMillis() + 5_000;
        while (server.status().connections() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(50);
        assertEquals(0, server.status().connections(), "the handler was released");
    }

    @Test void aSilentServerTimesOutAndTheClientCleansUp() throws Exception {
        try (ServerSocket mute = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            var client = client(trustTestAuthority(), new LinkedBlockingQueue<>(), "1.0.0", 400);
            long started = System.nanoTime();
            Thread connect = new Thread(() -> assertThrows(IOException.class,
                    () -> client.ensureConnected(new ServerRoute("127.0.0.1", mute.getLocalPort(), false))));
            connect.start();
            try (Socket accepted = mute.accept()) {
                connect.join(5_000);
                assertFalse(connect.isAlive());
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3_000);
                accepted.setSoTimeout(3_000);
                // The client closed its end: the server side sees the end of the stream.
                InputStream in = accepted.getInputStream();
                byte[] buffer = new byte[256];
                int total = 0;
                for (int read; (read = in.read(buffer)) > 0; ) total += read;
                assertTrue(total < 256, "only the stream header was written, never credentials");
            }
            assertFalse(client.isConnected());
            assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.getName().equals("deckduel-client-reader") && t.isAlive()),
                    "no reader thread was started");
        }
    }

    @Test void theServerRefusesOutdatedClientsBeforeLogin() throws Exception {
        int port = start(ServerConfig.defaults(0).withMinimumClientVersion("1.1.0"));
        ServerRoute route = new ServerRoute("127.0.0.1", port, false);
        for (String old : new String[] {"1.0.0", "1.1.0-rc.1", "development build"}) {
            var client = client(ClientTls.systemTrust(), new LinkedBlockingQueue<>(), old, 5_000);
            UpdateRequiredException refused = assertThrows(UpdateRequiredException.class, () -> client.ensureConnected(route), old);
            assertEquals("1.1.0", refused.minimumVersion());
            assertTrue(refused.getMessage().contains("1.1.0 or newer"));
            assertFalse(client.isConnected());
        }
        var inbox = new LinkedBlockingQueue<Message>();
        try (var current = client(ClientTls.systemTrust(), inbox, "1.1.0", 5_000)) {
            current.ensureConnected(route);
            current.send(new RegisterRequest("current_bob", "secret123"));
            assertTrue(inbox.poll(10, TimeUnit.SECONDS) instanceof LoginResponse response && response.ok());
        }
    }

    @Test void aRenewedCertificateIsServedToNewConnectionsWithoutARestart() throws Exception {
        Path chain = temporary.resolve("fullchain.pem"), key = temporary.resolve("privkey.pem");
        Files.copy(good.chain(), chain);
        Files.copy(good.key(), key);
        TlsMaterial material = TlsMaterial.load(chain, key);
        int port = start(ServerConfig.defaults(0).withTls(material));
        var before = peerCertificate(port);
        var inbox = new LinkedBlockingQueue<Message>();
        try (var playing = client(trustTestAuthority(), inbox, "1.0.0", 5_000)) {
            playing.ensureConnected(new ServerRoute("localhost", port, true));
            Files.copy(renewed.chain(), chain, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(renewed.key(), key, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            material.reload();
            var after = peerCertificate(port);
            assertNotEquals(before.getSerialNumber(), after.getSerialNumber(), "new connections get the renewed certificate");
            playing.send(new RegisterRequest("still_here", "secret123"));
            assertTrue(inbox.poll(10, TimeUnit.SECONDS) instanceof LoginResponse, "an existing connection keeps working");
        }
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n");
        assertThrows(IOException.class, material::reload, "a broken renewal is refused");
        assertEquals(material.certificate().getSerialNumber(), peerCertificate(port).getSerialNumber(), "and the last good certificate stays in use");
    }

    private static java.security.cert.X509Certificate peerCertificate(int port) throws Exception {
        try (SSLSocket socket = (SSLSocket) trustTestAuthority().getSocketFactory().createSocket("localhost", port)) {
            socket.startHandshake();
            return (java.security.cert.X509Certificate) socket.getSession().getPeerCertificates()[0];
        }
    }
}
