package com.grandis.nova.mockapi.global.chaos;

/**
 * 실패를 주입할 때 어떤 모양으로 실패할지.
 *
 * <p>둘 다 커밋 전에 발생하므로 아무것도 저장되지 않는다.
 */
public enum FailureMode {

    /** 즉시 500 UPSTREAM_UNAVAILABLE. */
    HTTP_5XX,

    /**
     * 응답하지 않은 채 연결을 유지하다 {@code mock.timeout-hold-ms} 뒤에 끊는다.
     * 워커 HTTP 읽기 타임아웃보다 길어야 워커가 타임아웃을 겪는다.
     */
    TIMEOUT
}
