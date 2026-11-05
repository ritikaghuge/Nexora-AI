package com.nexora.service;

import com.nexora.ai.ImageData;
import com.nexora.config.AppProperties;
import com.nexora.dto.Dtos.FileDto;
import com.nexora.exception.ApiException;
import com.nexora.model.UploadedFile;
import com.nexora.model.User;
import com.nexora.repository.FileRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private static final Set<String> DOC_EXT = Set.of("pdf", "docx", "txt", "md");
    private static final Set<String> IMG_EXT = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final int MAX_TEXT = 400_000;

    private final Path root;
    private final FileRepository files;
    private final DocumentExtractor extractor;

    public FileService(AppProperties props, FileRepository files, DocumentExtractor extractor) {
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        this.files = files;
        this.extractor = extractor;
    }

    public FileDto upload(User u, MultipartFile mf) {
        if (mf == null || mf.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "No file received");
        if (mf.getSize() > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (max 15 MB)");
        String original = sanitizeName(mf.getOriginalFilename());
        String ext = extension(original);
        boolean isDoc = DOC_EXT.contains(ext);
        boolean isImg = IMG_EXT.contains(ext);
        if (!isDoc && !isImg) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported file type. Allowed: PDF, DOCX, TXT, MD, PNG, JPG, WEBP, GIF.");
        }
        byte[] data;
        try {
            data = mf.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
        if (!signatureMatches(ext, data)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File content does not match its extension");
        }
        String text = null;
        String warning = null;
        if (isDoc) {
            text = extractor.extract(ext, data).replace("\u0000", "").strip();
            if (text.isEmpty()) warning = "No text could be extracted (scanned PDF?). Try a text-based file.";
            if (text.length() > MAX_TEXT) {
                text = text.substring(0, MAX_TEXT);
                warning = "Document is very long; only the first " + MAX_TEXT + " characters are used.";
            }
        }
        String stored = UUID.randomUUID() + "." + ext;
        try {
            Path dir = root.resolve(String.valueOf(u.id)).normalize();
            Path target = dir.resolve(stored).normalize();
            if (!target.startsWith(root)) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file path");
            Files.createDirectories(dir);
            Files.write(target, data);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the file");
        }
        UploadedFile f = new UploadedFile();
        f.userId = u.id;
        f.originalName = original;
        f.storedName = stored;
        f.contentType = mime(ext);
        f.kind = isImg ? "IMAGE" : "DOCUMENT";
        f.sizeBytes = data.length;
        f.extractedText = text;
        files.save(f);
        return toDto(f, warning);
    }

    public List<FileDto> list(Long userId) {
        return files.findByUserIdOrderByIdDesc(userId).stream().map(f -> toDto(f, null)).toList();
    }

    public void delete(Long userId, Long id) {
        UploadedFile f = require(userId, id);
        try {
            Files.deleteIfExists(path(f));
        } catch (IOException ignored) { /* row is removed anyway */ }
        files.delete(f);
    }

    public UploadedFile require(Long userId, Long id) {
        return files.findByIdAndUserId(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "File not found"));
    }

    public Path path(UploadedFile f) {
        return root.resolve(String.valueOf(f.userId)).resolve(f.storedName).normalize();
    }

    public byte[] readBytes(UploadedFile f) {
        try {
            return Files.readAllBytes(path(f));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File data is missing on disk");
        }
    }

    public ImageData imageData(UploadedFile f) {
        return new ImageData(f.contentType, Base64.getEncoder().encodeToString(readBytes(f)));
    }

    private FileDto toDto(UploadedFile f, String warning) {
        return new FileDto(f.id, f.originalName, f.kind, f.sizeBytes,
                f.extractedText == null ? 0 : f.extractedText.length(), f.conversationId, warning);
    }

    static String sanitizeName(String name) {
        String n = name == null ? "file" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        if (n.isEmpty() || n.startsWith(".")) n = "file" + n;
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }

    static String mime(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "text/plain";
        };
    }

    static boolean signatureMatches(String ext, byte[] d) {
        switch (ext) {
            case "pdf": return startsWith(d, "%PDF".getBytes(StandardCharsets.US_ASCII));
            case "docx": return d.length > 4 && d[0] == 'P' && d[1] == 'K';
            case "png": return startsWith(d, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
            case "jpg":
            case "jpeg": return startsWith(d, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "gif": return startsWith(d, "GIF8".getBytes(StandardCharsets.US_ASCII));
            case "webp": return d.length > 12 && startsWith(d, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
            default:
                for (int i = 0; i < Math.min(d.length, 4096); i++) if (d[i] == 0) return false;
                return true;
        }
    }

    private static boolean startsWith(byte[] d, byte[] sig) {
        if (d.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if (d[i] != sig[i]) return false;
        return true;
    }
}
