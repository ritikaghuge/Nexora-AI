package com.nexora;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

final class TestSupport {
    static final ObjectMapper OM = new ObjectMapper();
    static final String PASSWORD = "Str0ngPassw0rd!";

    record TestUser(long id, String email, MockHttpSession session) {}

    private TestSupport() {}

    static String uniqueEmail() { return "user-" + UUID.randomUUID() + "@example.com"; }

    static TestUser registerAndLogin(MockMvc mvc) throws Exception {
        String email = uniqueEmail();
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(OM.writeValueAsString(Map.of("email", email, "password", PASSWORD, "displayName", "Test User"))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode n = OM.readTree(reg.getResponse().getContentAsString());
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(OM.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        return new TestUser(n.get("id").asLong(), email, session);
    }
}
