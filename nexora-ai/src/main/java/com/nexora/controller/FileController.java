package com.nexora.controller;

import com.nexora.dto.Dtos.FileDto;
import com.nexora.model.UploadedFile;
import com.nexora.model.User;
import com.nexora.security.CurrentUserService;
import com.nexora.service.FileService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService files;
    private final CurrentUserService current;

    public FileController(FileService files, CurrentUserService current) {
        this.files = files;
        this.current = current;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileDto upload(@RequestParam("file") MultipartFile file, Authentication a) {
        return files.upload(current.require(a), file);
    }

    @GetMapping
    public List<FileDto> list(Authentication a) {
        return files.list(current.require(a).id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication a) {
        files.delete(current.require(a).id, id);
        return ResponseEntity.noContent().build();
    }

    /** Serves an uploaded IMAGE back to its owner (for thumbnails). */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long id, Authentication a) {
        User u = current.require(a);
        UploadedFile f = files.require(u.id, id);
        if (!"IMAGE".equals(f.kind)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(files.readBytes(f));
    }
}
