package com.nxenon.grpcweb.codec;

import java.util.Locale;

/**
 * The gRPC body encodings this extension handles.
 *
 * <p>{@link #TEXT} bodies are base64 over the frame stream; {@link #PROTO} and {@link #GRPC} bodies
 * are the frame stream as raw bytes. All three carry the same length-prefixed protobuf messages.
 */
public enum GrpcWebFormat {

    /** {@code application/grpc-web-text}: base64-encoded frames. */
    TEXT("application/grpc-web-text"),

    /** {@code application/grpc-web+proto}: binary frames. */
    PROTO("application/grpc-web+proto"),

    /**
     * {@code application/grpc}: native gRPC over HTTP/2, as grpc-go and the other core
     * implementations speak it. The body uses the same binary frames as {@link #PROTO}; only the
     * status travels differently, in HTTP/2 trailers rather than a trailer frame in the body.
     */
    GRPC("application/grpc");

    private final String canonicalContentType;

    GrpcWebFormat(String canonicalContentType) {
        this.canonicalContentType = canonicalContentType;
    }

    public String canonicalContentType() {
        return canonicalContentType;
    }

    /**
     * Identifies the format from a Content-Type header value.
     *
     * <p>Parameters such as {@code ; charset=utf-8} are ignored, matching is case-insensitive, and
     * the {@code +proto} suffix variants are accepted. Media types are compared whole, so neither
     * {@code application/grpc-web-text} nor {@code application/grpc} is swallowed by a shorter prefix,
     * and native gRPC with a non-protobuf codec such as {@code application/grpc+json} is not claimed.
     *
     * @param contentType a header value, which may be {@code null}
     * @return the matching format, or {@code null} if the value is not a gRPC content type
     */
    public static GrpcWebFormat fromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int parameterStart = contentType.indexOf(';');
        String mediaType = (parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType)
                .trim()
                .toLowerCase(Locale.ROOT);

        if (mediaType.equals("application/grpc-web-text")
                || mediaType.equals("application/grpc-web-text+proto")) {
            return TEXT;
        }
        if (mediaType.equals("application/grpc-web")
                || mediaType.equals("application/grpc-web+proto")) {
            return PROTO;
        }
        if (mediaType.equals("application/grpc")
                || mediaType.equals("application/grpc+proto")) {
            return GRPC;
        }
        return null;
    }
}
