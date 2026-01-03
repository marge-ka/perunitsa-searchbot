package searchengine.crawler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.extractor.TextExtractor;
import searchengine.fetcher.PageFetcher;
import searchengine.lemma.LemmaFinder;
import searchengine.model.Site;
import searchengine.saver.IndexSaver;
import searchengine.services.impl.IndexingManager;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class PageCrawler {

    private final PageFetcher pageFetcher;
    private final TextExtractor textExtractor;
    private final LemmaFinder lemmaFinder;
    private final IndexSaver indexSaver;
    private final IndexingManager indexingManager;

    @Autowired
    public PageCrawler(PageFetcher pageFetcher, TextExtractor textExtractor,
                       LemmaFinder lemmaFinder, IndexSaver indexSaver,
                       IndexingManager indexingManager) {
        this.pageFetcher = pageFetcher;
        this.textExtractor = textExtractor;
        this.lemmaFinder = lemmaFinder;
        this.indexSaver = indexSaver;
        this.indexingManager = indexingManager;
    }

    public void crawl(Site site) {
        log.info("Начинаем обход сайта: {}", site.getUrl());
        Set<String> visited = ConcurrentHashMap.newKeySet();
        int maxDepth = 10;

        int parallelism = Math.min(4, Runtime.getRuntime().availableProcessors());
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        indexingManager.registerForkJoinPool(site.getId(), pool);

        try {
            CrawlTask rootTask = new CrawlTask(site, "/", 0, visited, maxDepth,
                    pageFetcher, textExtractor, lemmaFinder, indexSaver);

            pool.invoke(rootTask);

            log.info("Завершён обход сайта: {}. Всего страниц: {}", site.getUrl(), visited.size());

        } catch (Exception e) {
            if (isCancellationException(e)) {
                log.info("Обход сайта {} был остановлен пользователем", site.getUrl());
                throw new IndexingStoppedException("Индексация остановлена пользователем");
            } else {
                log.error("Ошибка при обходе сайта: {}", site.getUrl(), e);
                throw e;
            }
        } finally {
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("ForkJoinPool для сайта {} не завершился вовремя", site.getUrl());
                }
            } catch (InterruptedException e) {
                log.warn("Прервано ожидание завершения ForkJoinPool");
                Thread.currentThread().interrupt();
            }
            indexingManager.unregisterSite(site.getId());
        }
    }

    private boolean isCancellationException(Exception e) {
        return e instanceof InterruptedException ||
                (e.getCause() != null && e.getCause() instanceof InterruptedException) ||
                Thread.currentThread().isInterrupted() ||
                (e.getMessage() != null && e.getMessage().contains("прервана"));
    }

    public static class IndexingStoppedException extends RuntimeException {
        public IndexingStoppedException(String message) {
            super(message);
        }
    }
}