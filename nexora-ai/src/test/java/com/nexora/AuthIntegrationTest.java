package com.nexora;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexora.model.User;
import com.nexora.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    private String json(Object o) throws Exception { return TestSupport.OM.writeValueAsString(o); }

    @Test
    void registerHashesPasswordWithBcrypt() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        User stored = users.findById(u.id()).orElseThrow();
        assertTrue(stored.passwordHash.startsWith("$2"), "password must be a BCrypt hash");
        assertNotEquals(TestSupport.PASSWORD, stored.passwordHash);
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", u.email(), "password", TestSupport.PASSWORD, "displayName", "Dup"))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "not-an-email", "password", "short", "displayName", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", u.email(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionGivesAccessToMe() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        mvc.perform(get("/api/auth/me").session(u.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(u.email()));
    }

    @Test
    void protectedEndpointsNeedLogin() throws Exception {
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/stream").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void newUserGetsDefaultOllamaProviderAndSettings() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        mvc.perform(get("/api/providers").session(u.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("OLLAMA"))
                .andExpect(jsonPath("$[0].preferred").value(true));
        mvc.perform(get("/api/settings").session(u.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.theme").value("dark"));
    }
}
