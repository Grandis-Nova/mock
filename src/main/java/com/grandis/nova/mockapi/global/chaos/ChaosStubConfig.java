package com.grandis.nova.mockapi.global.chaos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 제어 파트가 실제 구현을 올리기 전까지 쓰는 임시 빈.
 *
 * <p>등록 파트(NV-3)가 이것 때문에 막히지 않도록 아무 일도 하지 않는 구현을 둔다.
 * 실제 구현에 {@code @Component} 를 붙이면 여기 빈은 자동으로 물러난다.
 *
 * <p>{@code ConfigProvider} 스텁은 {@code MockConfigStore}(NV-6)가 올라와 지웠다. 남은 둘도
 * 실제 구현이 올라오는 대로 지운다 — 지연·실패 주입 NV-17, 결함 NV-18.
 */
@Configuration
public class ChaosStubConfig {

    /** 지연도 실패도 넣지 않는다. */
    @Bean
    @ConditionalOnMissingBean(FailureInjector.class)
    public FailureInjector stubFailureInjector() {
        return snapshot -> {
        };
    }

    /** 결함이 걸린 적이 없다고 답한다. */
    @Bean
    @ConditionalOnMissingBean(FaultHook.class)
    public FaultHook stubFaultHook() {
        return externalKey -> false;
    }
}
