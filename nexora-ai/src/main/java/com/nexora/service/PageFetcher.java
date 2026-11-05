package com.nexora.service;

import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

/** Downloads a public web page and returns readable text. Refuses private/local addresses (SSRF protection). */
@Service
public class PageFetcher {
    public Optional<String> fetchText(String url, int maxChars) {
        try {
            URI u = URI.create(url);
            if (u.getHost() == null || !(u.getScheme().equals("http") || u.getScheme().equals("https"))) return Optional.empty();
            for (InetAddress a : InetAddress.getAllByName(u.getHost())) {
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()) {
                    return Optional.empty();
                }
            }
            Document d = Jsoup.connect(url).userAgent("Mozilla/5.0 (compatible; NexoraAI/1.0)")
                    .timeout(8000).maxBodySize(1_500_000).get();
            d.select("script,style,nav,footer,header,aside,form,noscript,iframe").remove();
            String text = d.body().text().strip();
            if (text.isEmpty()) return Optional.empty();
            return Optional.of(text.length() > maxChars ? text.substring(0, maxChars) : text);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
