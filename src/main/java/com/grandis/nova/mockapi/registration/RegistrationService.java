package com.grandis.nova.mockapi.registration;

import com.grandis.nova.mockapi.global.chaos.ConfigProvider;
import com.grandis.nova.mockapi.global.chaos.ConfigSnapshot;
import com.grandis.nova.mockapi.global.chaos.ConnectionDropper;
import com.grandis.nova.mockapi.global.chaos.FailureInjector;
import com.grandis.nova.mockapi.global.chaos.FaultHook;
import com.grandis.nova.mockapi.global.error.ErrorCode;
import com.grandis.nova.mockapi.global.error.MockException;
import com.grandis.nova.mockapi.registration.RegistrationWriter.Attempt;
import com.grandis.nova.mockapi.registration.dto.RegisterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 예약 등록. 명세의 처리 순서 1~7단계를 이 순서대로 부른다.
 *
 * <pre>
 * [트랜잭션 밖]  1. 지연 대기   2. 실패율 판정          ← FailureInjector
 * [트랜잭션 안]  3~6. 잠금 · 취소 표식 · 재생 · 저장    ← RegistrationWriter (시도 한 번)
 *               중복 키면 트랜잭션을 나와 3단계부터 다시
 * [커밋 후]     7. 결함이 걸려 있으면 응답 없이 끊기   ← FaultHook · ConnectionDropper
 * </pre>
 *
 * <p><b>이 클래스에는 {@code @Transactional} 을 붙이지 않는다.</b> 붙이면 지연 대기가 트랜잭션 안으로
 * 들어가 그만큼 DB 커넥션이 묶인다. 지연 2초 · 풀 20개면 10 RPS 에서도 풀이 마른다.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    /**
     * 중복 키 재시도 상한. 키가 겹친 경우는 다음 시도에서 행을 찾아 끝나고, 번호가 겹칠 확률은
     * 하루 180만 건에서도 0.02% 이하라 한 번 더 하면 끝난다. 상한은 무한 루프를 막는 안전장치다.
     */
    static final int MAX_ATTEMPTS = 5;

    static final String RETRY_EXHAUSTED = "중복 키 재시도 상한(" + MAX_ATTEMPTS + "회)을 넘었습니다.";

    private final ConfigProvider configProvider;
    private final FailureInjector failureInjector;
    private final RegistrationWriter writer;
    private final FaultHook faultHook;
    private final ConnectionDropper connectionDropper;

    public RegistrationService(ConfigProvider configProvider, FailureInjector failureInjector,
                               RegistrationWriter writer, FaultHook faultHook,
                               ConnectionDropper connectionDropper) {
        this.configProvider = configProvider;
        this.failureInjector = failureInjector;
        this.writer = writer;
        this.faultHook = faultHook;
        this.connectionDropper = connectionDropper;
    }

    public RegisterResult register(String key, RegisterRequest request) {
        // 1~2. 이 시도가 쓸 설정을 먼저 얼린다. 처리 도중 설정이 바뀌어도 이 값으로 끝난다.
        // 실패는 커밋 전이라 아무것도 저장되지 않는다. TIMEOUT 이면 여기서 응답 없이 끝난다.
        ConfigSnapshot snapshot = configProvider.snapshot();
        failureInjector.apply(snapshot);

        Attempt attempt = writeRetryingOnDuplicateKey(key, request);

        // 7. 새로 커밋한 경우에만 발동한다. 재생은 커밋이 없어 "커밋 후 응답 유실" 이 아니다.
        if (!attempt.replayed() && faultHook.consumeResponseLost(key)) {
            connectionDropper.drop("RESPONSE_LOST_AFTER_COMMIT: " + key);
        }
        return new RegisterResult(attempt.registration(), attempt.replayed(), snapshot.configVersion());
    }

    private Attempt writeRetryingOnDuplicateKey(String key, RegisterRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return writer.attemptOnce(key, request);
            } catch (DataIntegrityViolationException e) {
                // 중복 키가 아닌 무결성 위반(CHECK · NOT NULL)은 Mock 의 버그다. 그대로 올려 로그에 남긴다.
                if (!DuplicateKey.isCause(e)) {
                    throw e;
                }
                // 상한을 넘기면 이름 있는 500 으로 바꾼다. 그냥 올리면 catch-all 의 기본 문장이 나가
                // 부하 시험 결과에서 "주입한 실패" 와 구분되지 않는다. 워커는 똑같이 재시도한다.
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("중복 키 재시도 상한 초과 (key={}, attempts={})", key, attempt, e);
                    throw new MockException(ErrorCode.UPSTREAM_UNAVAILABLE, RETRY_EXHAUSTED);
                }
                // 오류가 아니라 정상 분기다. 같은 키를 다른 요청이 먼저 만들었거나 번호가 겹쳤다.
                log.debug("중복 키 — 새 트랜잭션으로 다시 시도한다 (key={}, attempt={})", key, attempt);
            }
        }
    }
}
