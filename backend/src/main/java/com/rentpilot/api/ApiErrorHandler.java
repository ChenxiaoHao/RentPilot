package com.rentpilot.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e,HttpServletRequest request){
        Map<String,String> fields=new java.util.LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error->fields.putIfAbsent(error.getField(),"格式或长度不正确"));
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT","请检查填写的信息",request.getRequestURI(),fields));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> body(HttpServletRequest request){return error(400,"INVALID_INPUT","请求内容或日期格式不正确",request);}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest request) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), request.getRequestURI(), e.fieldErrors()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(HttpServletRequest request) {
        return error(404, "NOT_FOUND", "接口不存在", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpServletRequest request) {
        return error(405, "METHOD_NOT_ALLOWED", "接口仅支持指定的只读请求", request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> database(DataAccessException e, HttpServletRequest request) {
        log.error("Catalog database operation failed", e);
        return error(503, "DATABASE_UNAVAILABLE", "房源数据库暂不可用，请稍后重试", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected API error", e);
        return error(500, "INTERNAL_ERROR", "服务暂时无法完成请求", request);
    }

    private ResponseEntity<ApiError> error(int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(code, message, request.getRequestURI(), Map.of()));
    }

    public record ApiError(String code, String message, String path, Map<String, String> fieldErrors) {}
}
