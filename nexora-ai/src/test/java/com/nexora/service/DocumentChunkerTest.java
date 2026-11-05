package com.nexora.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DocumentChunkerTest {
    private String bigDoc() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("Paragraph ").append(i).append(" talks about generic filler content for padding purposes only. ".repeat(6)).append("\n\n");
            if (i == 150) sb.append("The warranty period for the NEXORA device is exactly thirty six months.\n\n");
        }
        return sb.toString();
    }

    @Test
    void shortDocumentIsReturnedWhole() {
        assertEquals("short text", DocumentChunker.select("short text", "anything", 1000));
    }

    @Test
    void relevantChunkIsSelectedForLongDocument() {
        String doc = bigDoc();
        String out = DocumentChunker.select(doc, "How long is the warranty period?", 6000);
        assertTrue(out.contains("thirty six months"));
        assertTrue(out.length() <= 8000, "result must stay near the budget");
    }

    @Test
    void genericQuestionSamplesEvenly() {
        String out = DocumentChunker.select(bigDoc(), "summarize", 6000);
        assertTrue(out.contains("Paragraph 0"));
        assertTrue(out.length() < bigDoc().length());
    }
}
