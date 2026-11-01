package com.rentpilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:amap-proxy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","rentpilot.amap.security-code=", "rentpilot.amap.web-key="})
@AutoConfigureMockMvc
class AmapProxyTest {
    @Autowired MockMvc mvc;
    @Test void unconfiguredProxyIsExplicitlyUnavailable() throws Exception {
        mvc.perform(get("/_AMapService/v3/log/init")).andExpect(status().isServiceUnavailable());
    }
    @Test void invalidPathsAreRejected() throws Exception {
        mvc.perform(get("/_AMapService/invalid..path")).andExpect(status().isBadRequest());
    }
}


@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:amap-proxy-configured;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "rentpilot.amap.security-code=test-secret", "rentpilot.amap.web-key=test-key"})
@AutoConfigureMockMvc
class AmapProxyConfiguredTest {
    @Autowired MockMvc mvc;
    @Test void rejectsWrongMissingAndDuplicateKeys() throws Exception {
        mvc.perform(get("/_AMapService/v3/log/init?key=wrong")).andExpect(status().isForbidden());
        mvc.perform(get("/_AMapService/v3/log/init")).andExpect(status().isForbidden());
        mvc.perform(get("/_AMapService/v3/log/init?key=test-key&key=test-key")).andExpect(status().isForbidden());
    }
    @Test void rejectsCallerSecurityCode() throws Exception {
        mvc.perform(get("/_AMapService/v3/log/init?key=test-key&jscode=anything")).andExpect(status().isBadRequest());
    }
}
