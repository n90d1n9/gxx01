package tech.kayys.wayang.sandbox.network;

public final class NetworkAccessException extends RuntimeException {

    public NetworkAccessException(String message) {
        super(message);
    }

    public NetworkAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
