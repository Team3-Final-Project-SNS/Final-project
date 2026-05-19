package com.example.team3final.common.exception;

import com.example.team3final.common.dto.response.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    // 예상치 못한 서버 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDto.of("COMMON_500", "서버 내부 오류가 발생했습니다."));
    }
}
