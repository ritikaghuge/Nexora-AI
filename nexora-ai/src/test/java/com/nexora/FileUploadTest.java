package com.nexora;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FileUploadTest {
    @Autowired MockMvc mvc;

    @Test
    void textFileIsAcceptedAndTextExtracted() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        MockMultipartFile f = new MockMultipartFile("file", "notes.txt", "text/plain", "Spring Boot is a Java framework.".getBytes());
        mvc.perform(multipart("/api/files").file(f).session(u.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("DOCUMENT"))
                .andExpect(jsonPath("$.chars").value(32));
    }

    @Test
    void dangerousExtensionIsRejected() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        MockMultipartFile f = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[]{'M', 'Z', 1, 2});
        mvc.perform(multipart("/api/files").file(f).session(u.session())).andExpect(status().isBadRequest());
    }

    @Test
    void fakePdfWithWrongContentIsRejected() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        MockMultipartFile f = new MockMultipartFile("file", "fake.pdf", "application/pdf", "this is not a pdf".getBytes());
        mvc.perform(multipart("/api/files").file(f).session(u.session())).andExpect(status().isBadRequest());
    }

    @Test
    void fakeImageIsRejected() throws Exception {
        TestSupport.TestUser u = TestSupport.registerAndLogin(mvc);
        MockMultipartFile f = new MockMultipartFile("file", "pic.png", "image/png", "<html>nope</html>".getBytes());
        mvc.perform(multipart("/api/files").file(f).session(u.session())).andExpect(status().isBadRequest());
    }

    @Test
    void uploadRequiresLogin() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/files").file(f)).andExpect(status().isUnauthorized());
    }
}
