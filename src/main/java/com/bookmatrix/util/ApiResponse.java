package com.bookmatrix.util;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Generic class: one envelope for every API response, whatever the data type T is.
 * Success: { "success": true,  "data": ..., "message": "..." }
 * Failure: { "success": false, "error": { "code", "message", "fields" } }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, ErrorBody error) {

    public record ErrorBody(String code, String message, Map<String, String> fields) {
    }

    /** Generic static method: the compiler infers T from the argument. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, null, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, Map<String, String> fields) {
        return new ApiResponse<>(false, null, null, new ErrorBody(code, message, fields));
    }
}
