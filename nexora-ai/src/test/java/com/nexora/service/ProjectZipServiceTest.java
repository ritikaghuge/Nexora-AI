package com.nexora.service;

import static org.junit.jupiter.api.Assertions.*;

import com.nexora.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ProjectZipServiceTest {
    private final ProjectZipService zips = new ProjectZipService();

    private List<String> names(byte[] zip) throws Exception {
        List<String> out = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) out.add(e.getName());
        }
        return out;
    }

    @Test
    void buildsZipFromFileBlocks() throws Exception {
        String md = "## Source files\n### File: src/main.js\n```javascript\nconsole.log('hi');\n```\n\n"
                + "### File: README.md\n```markdown\n# Demo\n```\n";
        List<String> n = names(zips.build(md));
        assertTrue(n.contains("nexora-project/src/main.js"), n.toString());
        assertTrue(n.contains("nexora-project/README.md"), n.toString());
    }

    @Test
    void blocksPathTraversal() throws Exception {
        String md = "### File: ../../evil.sh\n```sh\nrm -rf /\n```\n### File: /etc/passwd\n```\nx\n```\n### File: ok/a.txt\n```\nfine\n```\n";
        List<String> n = names(zips.build(md));
        assertEquals(List.of("nexora-project/ok/a.txt"), n);
    }

    @Test
    void rejectsMessagesWithoutFiles() {
        assertThrows(ApiException.class, () -> zips.build("just some chat text"));
    }
}
