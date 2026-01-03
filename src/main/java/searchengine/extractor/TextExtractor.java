package searchengine.extractor;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class TextExtractor {
    public String extract(String html) {
        return Jsoup.parse(html).text();
    }
}