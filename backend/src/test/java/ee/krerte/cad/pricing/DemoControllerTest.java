package ee.krerte.cad.pricing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.krerte.cad.WorkerClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DemoController.class)
@Import(PlanConfig.class)
class DemoControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private WorkerClient worker;
    @MockBean private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private static final String SPEC =
            "{\"template\":\"box\",\"params\":{\"width\":50,\"height\":30,\"depth\":20,\"wall\":2}}";

    @Test
    void firstCallReturns200() throws Exception {
        when(worker.generate(any())).thenReturn(new byte[] {0x53, 0x54, 0x4C});

        mvc.perform(
                        post("/api/demo/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SPEC))
                .andExpect(status().isOk());
    }

    @Test
    void thirdCallReturns429() throws Exception {
        when(worker.generate(any())).thenReturn(new byte[] {0x53, 0x54, 0x4C});

        // First two succeed
        for (int i = 0; i < 2; i++) {
            mvc.perform(
                            post("/api/demo/generate")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(SPEC))
                    .andExpect(status().isOk());
        }
        // Third is rate-limited
        mvc.perform(
                        post("/api/demo/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SPEC))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("demo_limit_reached"))
                .andExpect(jsonPath("$.upgrade_url").value("/pricing"));
    }
}
