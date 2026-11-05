package com.nexora;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexora.model.Conversation;
import com.nexora.model.Message;
import com.nexora.repository.ConversationRepository;
import com.nexora.repository.MessageRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ConversationApiTest {
    @Autowired MockMvc mvc;
    @Autowired ConversationRepository convs;
    @Autowired MessageRepository msgs;

    private Conversation conv(long userId, String title) {
        Conversation c = new Conversation();
        c.userId = userId;
        c.title = title;
        return convs.save(c);
    }

    @Test
    void listRenameMessagesAndDelete() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        Conversation c = conv(u.id(), "Java streams");
        Message m = new Message();
        m.conversationId = c.id; m.role = "USER"; m.content = "How do collectors work?";
        msgs.save(m);

        mvc.perform(get("/api/conversations").session(u.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/conversations").param("q", "streams").session(u.session()))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/conversations").param("q", "zzz-not-there").session(u.session()))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(patch("/api/conversations/" + c.id).session(u.session()).contentType(MediaType.APPLICATION_JSON)
                .content(TestSupport.OM.writeValueAsString(Map.of("title", "Renamed"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Renamed"));
        mvc.perform(get("/api/conversations/" + c.id + "/messages").session(u.session()))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].role").value("USER"));
        mvc.perform(delete("/api/conversations/" + c.id).session(u.session())).andExpect(status().isNoContent());
        mvc.perform(get("/api/conversations/" + c.id + "/messages").session(u.session())).andExpect(status().isNotFound());
    }

    @Test
    void usersCannotSeeEachOthersConversations() throws Exception {
        TestSupport.TestUser a = TestSupport.registerAndLogin(mvc);
        TestSupport.TestUser b = TestSupport.registerAndLogin(mvc);
        Conversation c = conv(a.id(), "Private");
        mvc.perform(get("/api/conversations/" + c.id + "/messages").session(b.session())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/conversations/" + c.id).session(b.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/conversations").session(b.session())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void clearAllRemovesHistory() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        conv(u.id(), "One");
        conv(u.id(), "Two");
        mvc.perform(delete("/api/conversations").session(u.session())).andExpect(status().isNoContent());
        mvc.perform(get("/api/conversations").session(u.session())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void memoryCrud() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        mvc.perform(post("/api/memory").session(u.session()).contentType(MediaType.APPLICATION_JSON)
                .content(TestSupport.OM.writeValueAsString(Map.of("content", "I study Computer Engineering"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/memory").session(u.session())).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/memory").session(u.session())).andExpect(status().isNoContent());
        mvc.perform(get("/api/memory").session(u.session())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void calculatorTool() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        mvc.perform(post("/api/tools/calc").session(u.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expression\":\"2+3*4\"}")).andExpect(jsonPath("$.result").value("14"));
        mvc.perform(post("/api/tools/calc").session(u.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expression\":\"1/0\"}")).andExpect(status().isBadRequest());
    }
}
