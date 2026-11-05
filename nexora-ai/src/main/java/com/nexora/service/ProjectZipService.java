package com.nexora.service;

import com.nexora.exception.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Turns a Project Builder answer ("### File: path" + fenced code) into a downloadable ZIP. */
@Service
public class ProjectZipService {
    private static final Pattern FILE = Pattern.compile(
            "(?m)^[#*\\s]*(?:File|Path|Filename)\\s*:\\s*`?([^\\s`*]+)`?[*\\s]*\\n+```[^\\n]*\\n(.*?)\\n```", Pattern.DOTALL);
    private static final int MAX_FILES = 200;
    private static final int MAX_BYTES = 5_000_000;

    public byte[] build(String content) {
        Map<String, String> files = new LinkedHashMap<>();
        Matcher m = FILE.matcher(content == null ? "" : content);
        while (m.find() && files.size() < MAX_FILES) {
            String path = cleanPath(m.group(1));
            if (path != null) files.put(path, m.group(2));
        }
        if (files.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "No files in '### File: path' + code block format were found in this message.");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int total = 0;
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                byte[] bytes = e.getValue().getBytes(StandardCharsets.UTF_8);
                total += bytes.length;
                if (total > MAX_BYTES) break;
                z.putNextEntry(new ZipEntry("nexora-project/" + e.getKey()));
                z.write(bytes);
                z.closeEntry();
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build the ZIP");
        }
        return bos.toByteArray();
    }

    /** Rejects absolute paths and path traversal (zip-slip). */
    static String cleanPath(String raw) {
        if (raw == null) return null;
        String p = raw.replace('\\', '/').trim();
        while (p.startsWith("./")) p = p.substring(2);
        if (p.isEmpty() || p.length() > 200 || p.startsWith("/") || p.contains(":") || p.endsWith("/")) return null;
        for (String seg : p.split("/")) if (seg.isEmpty() || seg.equals("..") || seg.equals(".")) return null;
        return p;
    }
}
