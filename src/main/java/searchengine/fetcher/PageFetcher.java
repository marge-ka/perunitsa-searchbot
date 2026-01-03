package searchengine.fetcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import searchengine.config.CrawlerConfig;

import java.io.IOException;
import java.util.Random;

@Component
@Slf4j
@RequiredArgsConstructor
public class PageFetcher {

    private final CrawlerConfig crawlerConfig;
    private final Random random = new Random();

    public FetchResult fetch(String url) throws IOException {
        if (crawlerConfig.getDelayBetweenRequestsMs() > 0) {
            try {
                int delay = crawlerConfig.getDelayBetweenRequestsMs() +
                        random.nextInt(1000);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Запрос прерван во время задержки");
            }
        }

        Connection connection = Jsoup.connect(url)
                .userAgent(crawlerConfig.getUserAgent())
                .referrer(crawlerConfig.getReferrer())
                .timeout(crawlerConfig.getTimeoutMs())
                .followRedirects(crawlerConfig.isFollowRedirects())
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .maxBodySize(10 * 1024 * 1024);

        connection.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1");

        Connection.Response response = connection.execute();
        int statusCode = response.statusCode();
        String html = response.body();

        log.debug("Загружено: {} (статус: {}, размер: {} байт)",
                url, statusCode, html.length());

        return new FetchResult(statusCode, html);
    }
}