package com.grandis.nova.mockapi.global.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Mock 이 계약대로 내는 오류. */
    @ExceptionHandler(MockException.class)
    public ResponseEntity<ErrorResponse> handleMock(MockException e) {
        ErrorCode code = e.errorCode();
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, e.getMessage(), e.externalNumber()));
    }

    /** 본문 검증 실패. 어느 필드가 문제인지 메시지에 담는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.INVALID_REQUEST.defaultMessage());
        return badRequest(detail);
    }

    /** Idempotency-Key 같은 필수 헤더 누락. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return badRequest(e.getHeaderName() + " 헤더가 필요합니다.");
    }

    /** 본문이 비었거나 JSON 이 깨진 경우. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return badRequest("요청 본문을 읽을 수 없습니다.");
    }

    /**
     * 없는 경로. 아래 catch-all 로 떨어지면 500 UPSTREAM_UNAVAILABLE 이 되는데,
     * 본 서비스가 그것을 일시 실패로 보고 재시도한다. 경로 오타는 재시도해도 소용없으므로 404 로 준다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, "그런 경로가 없습니다: " + e.getResourcePath()));
    }

    /**
     * 나머지. Mock 의 500 은 본 서비스 공통 오류(INTERNAL_ERROR)가 아니라 UPSTREAM_UNAVAILABLE 이다.
     * 본 서비스는 이것을 일시 실패로 보고 재시도한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리하지 못한 오류", e);
        ErrorCode code = ErrorCode.UPSTREAM_UNAVAILABLE;
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, message));
    }
}
