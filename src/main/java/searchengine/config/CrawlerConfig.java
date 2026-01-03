package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings.crawler")
public class CrawlerConfig {
    private String userAgent = "Mozilla/5.0 (compatible; PerunitsaSearchBot/1.0; https://github.com/marge-ka/perunitsa-searchbot.git)";
    private String referrer = "https://www.google.com";
    private int timeoutMs = 10000;
    private boolean followRedirects = true;
    private int delayBetweenRequestsMs = 0;
}