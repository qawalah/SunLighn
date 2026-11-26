package dev.qawalah.sunwhere.server;

/** An answer that is not a success: the status and one plain sentence. */
final class ApiError extends RuntimeException {
    final int status;

    ApiError(int status, String message) {
        super(message, null, false, false);
        this.status = status;
    }

    static ApiError bad(String m) { return new ApiError(400, m); }
    static ApiError unauthorized() { return new ApiError(401, "Sign in first (Authorization: Bearer token)."); }
    static ApiError forbidden(String m) { return new ApiError(403, m); }
    static ApiError notFound() { return new ApiError(404, "Not found."); }
    static ApiError tooMany() { return new ApiError(429, "Too many requests. Try again later."); }
    static ApiError tooMany(String m) { return new ApiError(429, m); }
}
