package com.grandis.nova.mockapi.global.chaos;

import com.grandis.nova.mockapi.global.config.MockProperties;
import org.springframework.stereotype.Component;

/**
 * 지연·실패 설정을 들고 있는 메모리 보관소.
 *
 * <p>ERD 가 설정 테이블을 두지 않기로 했으므로 값은 여기에만 있고, 재기동하면
 * application.yml 의 mock.* 로 돌아간다.
 */
@Component
public class MockConfigStore implements ConfigProvider {

    /** 지금 적용 중인 설정. 1단계에서는 바뀌지 않는다. */
    private final ConfigSnapshot current;

    public MockConfigStore(MockProperties properties) {
        this.current = new ConfigSnapshot(
                properties.registerLatencyMs(),
                properties.failureRate(),
                properties.failureMode(),
                0);
    }

    /**
     * 등록 시도 하나가 시작할 때 A 가 부른다.
     *
     * <p>받아간 스냅샷은 record 라 불변이다. 처리 도중 설정이 바뀌어도 그 시도는 이 값으로 끝난다.
     */
    @Override
    public ConfigSnapshot snapshot() {
        return current;
    }
}
