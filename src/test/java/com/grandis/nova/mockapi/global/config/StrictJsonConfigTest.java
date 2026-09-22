package com.grandis.nova.mockapi.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 앱 컨텍스트의 JSON 변환기가 모르는 필드를 거절하는지 본다.
 *
 * <p>슬라이스 시험(@WebMvcTest)은 이 설정을 스캔하지 않아서 직접 넣어줘야 한다. 거기서 통과해도
 * 운영에서 이 설정이 실제로 잡히는지는 알 수 없으므로, 전체 컨텍스트로 한 번 확인한다.
 */
@SpringBootTest
class StrictJsonConfigTest {

    @Autowired
    private JsonMapper jsonMapper;

    record Sample(int known) {
    }

    @Test
    @DisplayName("앱의 JSON 변환기는 모르는 필드를 거절한다")
    void rejectsUnknownProperties() {
        assertThatThrownBy(() -> jsonMapper.readValue("{\"known\":1,\"unknown\":2}", Sample.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
