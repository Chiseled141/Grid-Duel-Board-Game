package deckduel.config;

/**
 * Thrown when configuration loading or validation fails.
 * Includes key, value, and reason context for troubleshooting.
 */
public class ConfigException extends RuntimeException {

    private final String key;
    private final String value;
    private final String reason;

    public ConfigException(String key, String value, String reason) {
        super(formatMessage(key, value, reason));
        this.key = key;
        this.value = value;
        this.reason = reason;
    }

    public ConfigException(String key, String value, String reason, Throwable cause) {
        super(formatMessage(key, value, reason), cause);
        this.key = key;
        this.value = value;
        this.reason = reason;
    }

    private static String formatMessage(String key, String value, String reason) {
        StringBuilder sb = new StringBuilder();
        if (key != null) {
            sb.append("key='").append(key).append("', ");
        }
        if (value != null) {
            sb.append("value='").append(value).append("': ");
        }
        sb.append(reason);
        return sb.toString();
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }

    public String reason() {
        return reason;
    }
}
