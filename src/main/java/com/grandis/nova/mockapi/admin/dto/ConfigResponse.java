package com.grandis.nova.mockapi.admin.dto;

import com.grandis.nova.mockapi.global.chaos.ConfigSnapshot;
import com.grandis.nova.mockapi.global.chaos.FailureMode;
import com.grandis.nova.mockapi.global.chaos.MockConfigStore;
import java.time.Instant;

/**
 * 설정 조회·변경의 응답. 둘이 같은 형식이다.
 *
 * <p>요청값이 아니라 <b>실제 적용된 값</b>을 담는다. 적용 실패를 저장 성공으로 표시하지
 * 않기 위해서다(요구사항 4.4).
 *
 * <p>안쪽 모델({@link ConfigSnapshot})을 그대로 내보내지 않는다. 그쪽은 A 파트와의 계약이라
 * 응답에 필드를 늘릴 때마다 계약을 건드리게 된다.
 */
public record ConfigResponse(
        int registerLatencyMs,
        double failureRate,
        FailureMode failureMode,
        int configVersion,
        Instant appliedAt
) {

    /** 중첩된 Applied 를 평평한 응답 모양으로 편다. */
    public static ConfigResponse from(MockConfigStore.Applied applied) {
        ConfigSnapshot snapshot = applied.snapshot();
        return new ConfigResponse(
                snapshot.registerLatencyMs(),
                snapshot.failureRate(),
                snapshot.failureMode(),
                snapshot.configVersion(),
                applied.appliedAt());
    }
}
