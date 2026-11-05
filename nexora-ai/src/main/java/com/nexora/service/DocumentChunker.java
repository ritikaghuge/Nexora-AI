package com.nexora.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Picks the parts of a long document most relevant to the question so they fit in the model's context. */
public final class DocumentChunker {
    private static final int CHUNK = 1400;
    private static final Set<String> STOP = Set.of("this", "that", "with", "from", "what", "which", "about", "does",
            "have", "will", "your", "their", "there", "these", "those", "please", "document", "explain", "give", "tell");

    private DocumentChunker() {}

    public static String select(String text, String query, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        List<String> chunks = split(text);
        int n = chunks.size();
        Set<String> terms = terms(query);
        int[] score = new int[n];
        int best = 0;
        for (int i = 0; i < n; i++) {
            String low = chunks.get(i).toLowerCase();
            for (String t : terms) if (low.contains(t)) score[i]++;
            best = Math.max(best, score[i]);
        }
        TreeSet<Integer> picked = new TreeSet<>();
        if (best == 0) {
            int count = Math.max(1, maxChars / CHUNK);
            double step = (double) n / count;
            for (int k = 0; k < count; k++) picked.add(Math.min(n - 1, (int) Math.floor(k * step)));
        } else {
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < n; i++) idx.add(i);
            idx.sort(Comparator.<Integer>comparingInt(i -> -score[i]).thenComparingInt(i -> i));
            int used = 0;
            picked.add(0);
            used += chunks.get(0).length();
            for (int i : idx) {
                if (picked.contains(i)) continue;
                int len = chunks.get(i).length();
                if (used + len > maxChars) continue;
                picked.add(i);
                used += len;
            }
        }
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        for (int i : picked) {
            if (prev >= 0 && i != prev + 1) sb.append("\n[...]\n");
            sb.append(chunks.get(i)).append('\n');
            prev = i;
        }
        return sb.toString().strip();
    }

    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String para : text.split("\\n\\s*\\n")) {
            String p = para.strip();
            if (p.isEmpty()) continue;
            while (p.length() > CHUNK * 2) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                out.add(p.substring(0, CHUNK));
                p = p.substring(CHUNK);
            }
            cur.append(p).append("\n\n");
            if (cur.length() >= CHUNK) { out.add(cur.toString().strip()); cur.setLength(0); }
        }
        if (cur.length() > 0) out.add(cur.toString().strip());
        if (out.isEmpty()) out.add(text.substring(0, Math.min(text.length(), CHUNK)));
        return out;
    }

    static Set<String> terms(String q) {
        Set<String> t = new HashSet<>();
        if (q == null) return t;
        for (String w : q.toLowerCase().split("[^\\p{L}\\p{N}]+")) if (w.length() >= 4 && !STOP.contains(w)) t.add(w);
        return t;
    }
}
