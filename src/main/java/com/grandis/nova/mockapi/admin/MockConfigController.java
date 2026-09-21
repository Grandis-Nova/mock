package com.grandis.nova.mockapi.admin;

import com.grandis.nova.mockapi.admin.dto.ConfigResponse;
import com.grandis.nova.mockapi.global.chaos.MockConfigStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지연·실패 설정 조회와 변경. 시연 조작 패널이다.
 *
 * <p>이 경로에는 <b>지연·실패를 주입하지 않는다.</b> 주입하면 실패율을 1.0 으로 올린 뒤 되돌릴 수
 * 없게 되어 시연 중에 Mock 을 살릴 방법이 사라진다.
 *
 * <p>워커가 부르는 API 가 아니므로 잘못된 입력은 전부 400 이다. 500 은 워커에게 "일시 실패,
 * 다시 보내라" 는 뜻이라 조작 패널이 낼 응답이 아니다.
 */
@RestController
@RequestMapping("/external/config")
public class MockConfigController {

    private final MockConfigStore store;

    public MockConfigController(MockConfigStore store) {
        this.store = store;
    }

    @GetMapping
    public ConfigResponse get() {
        return ConfigResponse.from(store.applied());
    }
}
