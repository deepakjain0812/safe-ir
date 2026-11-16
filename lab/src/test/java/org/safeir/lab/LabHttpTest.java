package org.safeir.lab;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"lab.role=inventory", "lab.downstream-url=", "lab.token=test-lab"})
class LabHttpTest {
    @Autowired MockMvc mvc;
    @Autowired FaultState faults;

    @BeforeEach void reset() { faults.set(FaultState.Mode.NONE, 0); }

    @Test void returnsRealSuccessForHealthyService() throws Exception {
        mvc.perform(get("/api/work")).andExpect(status().isOk())
            .andExpect(jsonPath("$.service").value("inventory"));
    }

    @Test void deniesFaultInjectionWithoutAuthorization() throws Exception {
        mvc.perform(post("/lab/admin/fault").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"ERROR\",\"delayMs\":0}"))
            .andExpect(status().isForbidden());
    }

    @Test void injectedFailureReturns503AndResetRestores200() throws Exception {
        mvc.perform(post("/lab/admin/fault").header("X-Lab-Token", "test-lab")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"ERROR\",\"delayMs\":0}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/work")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/lab/admin/fault").header("X-Lab-Token", "test-lab")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"NONE\",\"delayMs\":0}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/work")).andExpect(status().isOk());
    }

    @Test void invalidDelayIsRejected() throws Exception {
        mvc.perform(post("/lab/admin/fault").header("X-Lab-Token", "test-lab")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"LATENCY\",\"delayMs\":10000}"))
            .andExpect(status().isBadRequest());
    }
}
