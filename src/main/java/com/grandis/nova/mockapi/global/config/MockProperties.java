package com.grandis.nova.mockapi.global.config;

import com.grandis.nova.mockapi.global.chaos.FailureMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 지연·실패 설정의 기본값. 환경변수나 application.yml 로 준다.
 *
 * <p>ERD 가 설정 테이블을 두지 않기로 했으므로 실행 중 변경값은 메모리에만 있고,
 * 재기동하면 여기 값으로 돌아간다.
 */
@ConfigurationProperties(prefix = "mock")
public record MockProperties(

        /** 등록 요청마다 일부러 기다리는 시간. */
        @DefaultValue("500") int registerLatencyMs,

        /** 등록 요청이 일시 실패할 확률. 0 ~ 1. */
        @DefaultValue("0.05") double failureRate,

        @DefaultValue("HTTP_5XX") FailureMode failureMode,

        /** TIMEOUT 모드에서 응답 없이 연결을 유지하는 시간. */
        @DefaultValue("5000") long timeoutHoldMs
) {
}
