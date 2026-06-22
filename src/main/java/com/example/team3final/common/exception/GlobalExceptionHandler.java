package com.example.team3final.common.exception;

import com.example.team3final.common.dto.response.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Service 예외 처리 로직
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorResponseDto> handleServiceException(ServiceException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponseDto.of(errorCode.getCode(), errorCode.getMessage()));
    }

    // Valid 검증 실패 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of("Validation Failed", message));
    }

    // 필수 헤더 누락 (X-User-Id 등)
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingRequestHeader(MissingRequestHeaderException e) {
        String message = "필수 헤더가 누락되었습니다: " + e.getHeaderName();
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED) // 401
                .body(ErrorResponseDto.of("AUTH_001", message));
    }

    // 필수 쿼리파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingParameter(MissingServletRequestParameterException e) {
        String message = "필수 파라미터가 누락되었습니다: " + e.getParameterName();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of("COMMON_002", message));
    }

    // 파라미터 타입 변환 실패
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "파라미터 형식이 올바르지 않습니다: " + e.getName();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of("COMMON_003", message));
    }

    // Request Body JSON 파싱 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDto.of("COMMON_004", "요청 본문을 읽을 수 없습니다. JSON 형식을 확인하세요."));
    }

    // SSE 연결 타임아웃은 정상 종료 흐름이므로 500 응답으로 처리하지 않는다.
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        log.debug("[SSE] 비동기 요청 타임아웃 - 정상 종료");
    }

    // 예상치 못한 서버 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(Exception e, HttpServletRequest request) {
        log.error(
                "Unhandled exception. method={}, uri={}, query={}",
                request.getMethod(),
                request.getRequestURI(),
                maskSensitiveQuery(request.getQueryString()),
                e
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto.of("COMMON_500", "서버 내부 오류가 발생했습니다."));
    }

    private String maskSensitiveQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }

        String[] params = queryString.split("&");
        for (int i = 0; i < params.length; i++) {
            String param = params[i];
            int equalsIndex = param.indexOf('=');
            String key = equalsIndex >= 0 ? param.substring(0, equalsIndex) : param;

            if (isSensitiveQueryKey(key)) {
                params[i] = key + "=***";
            }
        }

        return String.join("&", params);
    }

    private boolean isSensitiveQueryKey(String key) {
        String normalizedKey = key.toLowerCase();
        return normalizedKey.equals("token")
                || normalizedKey.equals("access_token")
                || normalizedKey.equals("refresh_token")
                || normalizedKey.equals("authorization");
    }
}
