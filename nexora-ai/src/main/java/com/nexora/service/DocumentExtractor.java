package com.nexora.service;

import com.nexora.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DocumentExtractor {
    public String extract(String ext, byte[] data) {
        try {
            switch (ext) {
                case "pdf":
                    try (PDDocument d = Loader.loadPDF(data)) {
                        return new PDFTextStripper().getText(d);
                    }
                case "docx":
                    try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(data));
                         XWPFWordExtractor x = new XWPFWordExtractor(d)) {
                        return x.getText();
                    }
                default:
                    return new String(data, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not read this " + ext.toUpperCase() + " file (it may be corrupted or password-protected).");
        }
    }
}
