package com.grandis.nova.mockapi.global.chaos;

import com.grandis.nova.mockapi.global.config.MockProperties;
import com.grandis.nova.mockapi.global.error.ErrorCode;
import com.grandis.nova.mockapi.global.error.MockException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 응답을 주지 않고 연결을 붙잡아 둔다. 호출한 쪽은 끝내 아무 응답도 받지 못한다.
 *
 * <p>워커가 결과를 <b>모르는</b> 상황(UNKNOWN)을 만드는 유일한 수단이다. 두 곳이 쓴다.
 * <ul>
 *   <li>{@code failureMode=TIMEOUT} — 커밋 전, 주사위에 걸렸을 때</li>
 *   <li>{@code RESPONSE_LOST_AFTER_COMMIT} 결함 — 커밋 직후</li>
 * </ul>
 *
 * <p><b>방법:</b> 본문 길이를 선언해 헤더만 내보내고 본문을 쓰지 않는다. 클라이언트는 약속된
 * 본문을 기다리다 자기 읽기 타임아웃에 걸린다.
 *
 * <p>다른 방법은 전부 결국 응답을 보내서 쓸 수 없었다. 대기만 하면 시간이 지난 뒤 200 이 나가고,
 * 비동기로 열어두면 503 이 나가며, 예외를 던지면 {@code GlobalExceptionHandler} 가 잡아 500 으로
 * 바꾼다. 워커에게 200·500 은 각각 "성공" 과 "일시 실패" 라는 분명한 뜻이라 UNKNOWN 이 되지 않는다.
 *
 * <p>{@code mock.timeout-hold-ms} 만큼 붙잡은 뒤 약속한 본문을 채워 응답을 끝낸다. 붙잡기만 하면
 * 클라이언트가 끊어 줄 때까지 연결이 회수되지 않아 부하 시험에서 쌓인다. 1,000 RPS 에 유지 10초면
 * 약 1만 개로 톰캣 상한(8,192)을 넘는다.
 *
 * <p>유지 시간은 <b>워커 읽기 타임아웃보다 길어야 한다.</b> 짧으면 워커가 타임아웃 대신 응답을
 * 받아버려 재현하려던 상황이 아니게 된다. 명세가 "워커 타임아웃 + 2초" 로 정한 이유다.
 *
 * <p>붙잡는 동안 스레드 하나가 잔다. 가상 스레드라 수천 개가 동시에 자도 부담이 적다.
 */
@Component
public class ConnectionDropper {

    private static final Logger log = LoggerFactory.getLogger(ConnectionDropper.class);

    /** 실제로 보낼 일이 없는 길이. 클라이언트는 이만큼을 기다리다 포기한다. */
    private static final int PROMISED_BODY_LENGTH = 1024;

    /** 내용이 0 으로 고정이라 한 번만 만든다. 부하 시험에서 건마다 새로 만들지 않는다. */
    private static final byte[] FILLER = new byte[PROMISED_BODY_LENGTH];

    private final MockProperties properties;

    public ConnectionDropper(MockProperties properties) {
        this.properties = properties;
    }

    /**
     * 지금 처리 중인 요청의 응답을 영영 완성하지 않는다.
     *
     * <p>헤더만 내보낸 뒤 {@link ResponseLostException} 을 던져 이후 처리를 멈춘다. 던지지 않으면
     * 호출한 쪽이 계속 진행해 본문을 써버린다.
     *
     * @param reason 로그에 남길 이유
     * @throws ResponseLostException 언제나 던진다. 이 메서드는 정상 반환하지 않는다
     */
    public void drop(String reason) {
        HttpServletResponse response = currentResponse();
        try {
            // 상태는 500 이다. 유지 시간이 워커 읽기 타임아웃보다 짧아 워커가 끝까지 읽어버리면
            // 200 은 "성공" 으로 읽히지만 500 은 "일시 실패" 라 재시도로 정리된다.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentLength(PROMISED_BODY_LENGTH);
            response.flushBuffer();

            hold();

            // 약속한 길이를 채워 응답을 끝낸다. 클라이언트는 이미 포기했으므로 받지 않는다.
            // 이 쓰기가 있어야 연결이 회수된다.
            response.getOutputStream().write(FILLER);
            response.flushBuffer();
        } catch (IOException e) {
            if (!response.isCommitted()) {
                // 헤더조차 못 나갔다. 여기서 ResponseLostException 을 던지면 Advice 가 아무것도
                // 쓰지 않아 200 빈 응답이 나가고, 워커는 그것을 성공으로 읽는다. 등록되지 않은
                // 예약이 확정되는 최악의 경우라 일시 실패로 바꾼다.
                log.warn("연결 끊기 실패 — 헤더 전 오류라 일시 실패로 바꾼다", e);
                throw new MockException(ErrorCode.UPSTREAM_UNAVAILABLE);
            }
            // 이미 헤더가 나간 뒤라면 클라이언트가 먼저 끊었다는 뜻이고, 목적은 달성됐다.
            log.debug("연결 끊기 중 입출력 오류 — 이미 끊긴 연결로 본다", e);
        }
        throw new ResponseLostException(reason);
    }

    private void hold() {
        try {
            Thread.sleep(properties.timeoutHoldMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseLostException("붙잡는 도중 중단됐다");
        }
    }

    private HttpServletResponse currentResponse() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet && servlet.getResponse() != null) {
            return servlet.getResponse();
        }
        throw new IllegalStateException("서블릿 요청 안에서만 쓸 수 있다");
    }
}
