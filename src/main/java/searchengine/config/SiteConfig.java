package searchengine.config;

import lombok.Getter;
import lombok.Setter;

import java.net.MalformedURLException;
import java.net.URL;

@Setter
@Getter
public class SiteConfig {
    private String url;
    private String name;

    public boolean isUrlBelongsToSite(String pageUrl) {
        try {
            URL siteURL = new URL(url);
            URL pageURL = new URL(pageUrl);

            return siteURL.getHost().equals(pageURL.getHost());
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public String normalizeUrl() {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}