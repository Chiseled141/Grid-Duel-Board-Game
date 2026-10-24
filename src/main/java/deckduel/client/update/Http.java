package deckduel.client.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Plain HTTPS GETs with connect and read timeouts, a size limit and no credentials, as direct
 * S3 object downloads need. Failures become messages a player can act on.
 */
final class Http {
    static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    static final int READ_TIMEOUT_MILLIS = 30_000;

    private Http() { }

    /** Opens a GET, optionally from byte {@code from} onwards; the caller closes the connection. */
    static HttpURLConnection open(URI url, long from) throws IOException {
        UpdateConfig.checkedUrl(url);
        HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "DeckDuel-Updater");
        if (from > 0) connection.setRequestProperty("Range", "bytes=" + from + "-");
        return connection;
    }

    /** Reads a small whole resource, refusing more than {@code limit} bytes. */
    static byte[] fetch(URI url, int limit) throws IOException {
        HttpURLConnection connection = open(url, 0);
        try {
            int status = status(connection, url);
            if (status != 200) throw new IOException(describe(status, url));
            if (connection.getContentLengthLong() > limit) throw new IOException("Unexpectedly large response from " + url.getHost());
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int read; (read = in.read(buffer)) > 0; ) {
                    bytes.write(buffer, 0, read);
                    if (bytes.size() > limit) throw new IOException("Unexpectedly large response from " + url.getHost());
                }
                return bytes.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    /** The response status, with connection failures explained. */
    static int status(HttpURLConnection connection, URI url) throws IOException {
        try {
            return connection.getResponseCode();
        } catch (UnknownHostException unknown) {
            throw new IOException("Cannot find the update server " + url.getHost() + ". Check your internet connection.", unknown);
        } catch (java.net.SocketTimeoutException slow) {
            throw new IOException("The update server " + url.getHost() + " did not answer in time.", slow);
        } catch (java.net.ConnectException refused) {
            throw new IOException("Cannot reach the update server " + url.getHost() + ".", refused);
        }
    }

    static String describe(int status, URI url) {
        return switch (status) {
            case 403, 404 -> "The update file was not found on " + url.getHost() + " (HTTP " + status + ").";
            default -> "The update server " + url.getHost() + " answered HTTP " + status + ".";
        };
    }
}
