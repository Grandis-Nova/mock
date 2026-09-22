package com.grandis.nova.mockapi.global.error;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.constraints.Size;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 잘못된 요청이 500 으로 나가지 않는지 본다.
 *
 * <p>Mock 의 500 은 "일시 실패" 라는 뜻이고 본 서비스가 재시도한다. 요청 자체가 틀린 경우는
 * 몇 번을 다시 보내도 결과가 같으므로 4xx 로 나가야 재시도가 멈춘다. 실제 API 컨트롤러는 아직 없어서
 * 여기서만 쓰는 시험용 컨트롤러로 각 예외를 일으킨다.
 */
// 범위를 시험용 컨트롤러로 좁힌다. 범위 없는 @WebMvcTest 는 컨트롤러를 전부 긁어오므로,
// 누군가 새 컨트롤러를 만들 때마다 그 컨트롤러가 의존하는 빈이 없다며 이 시험이 깨진다.
@WebMvcTest(controllers = {GlobalExceptionHandlerTest.ProbeController.class,
        GlobalExceptionHandlerTest.PlainProbeController.class})
@Import({GlobalExceptionHandlerTest.ProbeController.class,
        GlobalExceptionHandlerTest.PlainProbeController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("메서드 오타 - 경로는 맞는데 메서드가 다르면 400")
    void methodNotSupported() throws Exception {
        mvc.perform(get("/probe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.replayable").value(false));
    }

    @Test
    @DisplayName("Content-Type 누락 - 본문을 읽을 수 없으면 400")
    void mediaTypeNotSupported() throws Exception {
        mvc.perform(post("/probe").content("{\"value\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("경로 변수 타입 불일치 - 숫자 자리에 글자가 오면 400")
    void typeMismatch() throws Exception {
        mvc.perform(get("/probe/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("경로 변수 길이 검증 - @Validated 가 붙은 컨트롤러")
    void constraintViolation() throws Exception {
        mvc.perform(get("/probe/" + "k".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    /**
     * 같은 길이 위반인데 {@code @Validated} 가 없으면 스프링이 다른 예외를 던진다.
     * 컨트롤러마다 애너테이션을 기억해야 한다면 언젠가 한 번은 빠뜨린다. 빠뜨린 경로가 통째로
     * 500 이 되면 워커가 그 요청을 영원히 재시도하므로, 둘 다 400 이어야 한다.
     */
    @Test
    @DisplayName("경로 변수 길이 검증 - @Validated 가 없는 컨트롤러도 400")
    void methodValidationWithoutValidated() throws Exception {
        mvc.perform(get("/plain/" + "k".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("없는 경로 - 404 이며 오류 형식을 지킨다")
    void noResource() throws Exception {
        mvc.perform(get("/없는경로"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                // 값이 없어도 필드를 생략하지 않는다(명세). doesNotExist() 는 필드가 아예 없어도
                // 통과해서 이 계약을 못 잡는다. value(nullValue()) 는 "있고 null" 만 통과한다.
                .andExpect(jsonPath("$.externalNumber").value(nullValue()));
    }

    @Test
    @DisplayName("Mock 안에서 터진 오류만 500 UPSTREAM_UNAVAILABLE 이다")
    void unexpectedStaysServerError() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("UPSTREAM_UNAVAILABLE"));
    }

    @RestController
    @Validated
    static class ProbeController {

        @PostMapping(path = "/probe", consumes = MediaType.APPLICATION_JSON_VALUE)
        String post(@RequestBody Map<String, String> body) {
            return "ok";
        }

        @GetMapping("/probe/{key}")
        String get(@PathVariable @Size(min = 1, max = 100) String key) {
            if ("boom".equals(key)) {
                throw new IllegalStateException("Mock 내부 오류");
            }
            return key;
        }

        @GetMapping("/probe/number/{n}")
        String number(@PathVariable int n) {
            return String.valueOf(n);
        }
    }

    /** {@code @Validated} 를 일부러 붙이지 않았다. */
    @RestController
    static class PlainProbeController {

        @GetMapping("/plain/{key}")
        String get(@PathVariable @Size(min = 1, max = 100) String key) {
            return key;
        }
    }
}
