package com.grandis.nova.mockapi.global.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@link ResponseLostException} 을 아무것도 쓰지 않고 삼킨다.
 *
 * <p>{@code GlobalExceptionHandler} 의 catch-all 이 먼저 잡으면 500 이 나가 UNKNOWN 이 아니라
 * "일시 실패" 가 되어버린다. 그래서 우선순위를 가장 높게 둔다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ResponseLostAdvice {

    private static final Logger log = LoggerFactory.getLogger(ResponseLostAdvice.class);

    @ExceptionHandler(ResponseLostException.class)
    public void handle(ResponseLostException e) {
        log.debug("응답을 보내지 않는다: {}", e.getMessage());
    }
}
