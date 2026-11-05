package com.nexora;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProviderApiTest {
    @Autowired MockMvc mvc;

    @Test
    void apiKeyIsNeverReturnedAndBadUrlRejected() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        String secret = "sk-super-secret-key-123";
        String body = mvc.perform(post("/api/providers").session(u.session()).contentType(MediaType.APPLICATION_JSON)
                .content(TestSupport.OM.writeValueAsString(Map.of("name", "Groq", "type", "OPENAI_COMPATIBLE",
                        "baseUrl", "https://api.groq.com/openai/v1", "apiKey", secret, "defaultModel", "m"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasApiKey").value(true))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(secret));

        mvc.perform(post("/api/providers").session(u.session()).contentType(MediaType.APPLICATION_JSON)
                .content(TestSupport.OM.writeValueAsString(Map.of("name", "Bad", "type", "OLLAMA", "baseUrl", "file:///etc/passwd"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/providers").session(u.session()).contentType(MediaType.APPLICATION_JSON)
                .content(TestSupport.OM.writeValueAsString(Map.of("name", "Bad", "type", "EVIL", "baseUrl", "http://localhost"))))
                .andExpect(status().isBadRequest());
    }
}
