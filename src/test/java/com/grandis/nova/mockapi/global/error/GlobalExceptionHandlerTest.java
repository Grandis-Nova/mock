package com.grandis.nova.mockapi.global.error;

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
@WebMvcTest
@Import(GlobalExceptionHandlerTest.ProbeController.class)
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
    @DisplayName("경로 변수 길이 검증 - 100 자를 넘으면 400")
    void constraintViolation() throws Exception {
        mvc.perform(get("/probe/" + "k".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("없는 경로 - 404 이며 오류 형식을 지킨다")
    void noResource() throws Exception {
        mvc.perform(get("/없는경로"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.externalNumber").doesNotExist());
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
}
