package com.grandis.nova.mockapi.global.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * 요청 본문에 계약에 없는 필드가 오면 400 으로 거절한다.
 *
 * <p>스프링 부트 기본값은 모르는 필드를 조용히 버린다. 설정 API 에 timeoutHoldMs 를 넣어도 200 이 나와
 * 적용된 줄 알게 되고, 워커가 계약에 없는 필드를 보내도 아무도 모른다. 같은 키로 다른 내용이 오면
 * 422 로 거절하는 것과 같은 원칙이다 — 계약 오류를 조용히 넘기지 않는다.
 *
 * <p>application.yml 이 아니라 코드에 둔다. application.yml 은 각자 예제를 복사해 쓰는 파일이라,
 * 예제에 한 줄 추가해도 이미 복사해둔 사본에는 들어가지 않아 기계마다 동작이 갈린다.
 *
 * <p>대신 DTO 에 명세의 필드를 전부 선언해야 한다. 저장하지 않는 필드(취소의 reason)도 마찬가지다.
 * 빠뜨리면 정상 요청이 400 이 된다.
 */
@Configuration
public class StrictJsonConfig {

    @Bean
    JsonMapperBuilderCustomizer failOnUnknownProperties() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
