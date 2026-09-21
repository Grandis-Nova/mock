package com.grandis.nova.mockapi.global.chaos;

/**
 * 등록 시도 하나가 시작할 때 고정하는 설정값.
 *
 * <p>처리 도중 설정이 바뀌어도 그 시도는 시작 시점의 값으로 끝난다.
 * {@code configVersion} 은 응답 헤더 {@code X-Mock-Config-Version} 으로 나간다.
 */
public record ConfigSnapshot(
        int registerLatencyMs,
        double failureRate,
        FailureMode failureMode,
        int configVersion
) {
}
