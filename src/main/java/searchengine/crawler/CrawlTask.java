package searchengine.crawler;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.extractor.TextExtractor;
import searchengine.fetcher.FetchResult;
import searchengine.fetcher.PageFetcher;
import searchengine.lemma.LemmaFinder;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.saver.IndexSaver;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class CrawlTask extends RecursiveAction {
    private final Site site;
    private final String path;
    private final int depth;
    private final Set<String> visited;
    private final int maxDepth;

    private final PageFetcher pageFetcher;
    private final TextExtractor textExtractor;
    private final LemmaFinder lemmaFinder;
    private final IndexSaver indexSaver;

    public CrawlTask(Site site, String path, int depth, Set<String> visited, int maxDepth,
                     PageFetcher pageFetcher, TextExtractor textExtractor,
                     LemmaFinder lemmaFinder, IndexSaver indexSaver) {
        this.site = site;
        this.path = path;
        this.depth = depth;
        this.visited = visited;
        this.maxDepth = maxDepth;
        this.pageFetcher = pageFetcher;
        this.textExtractor = textExtractor;
        this.lemmaFinder = lemmaFinder;
        this.indexSaver = indexSaver;
    }
    @Override
    protected void compute() {
        if (Thread.currentThread().isInterrupted()) {
            log.debug("Задача прервана на старте, path: {}", path);
            return;
        }

        if (depth > maxDepth) {
            return;
        }

        String fullUrl = getFullUrl();
        if (!visited.add(fullUrl)) {
            return;
        }

        try {
            checkInterruption();
            FetchResult result = pageFetcher.fetch(fullUrl);
            checkInterruption();

            int statusCode = result.getStatusCode();

            if (statusCode == 200) {
                String html = result.getHtml();
                processSuccessfulPage(fullUrl, html);
            } else {
                log.debug("Пропущена страница (статус {}): {}", statusCode, fullUrl);
            }
        } catch (IOException e) {
            if (Thread.currentThread().isInterrupted()) {
                log.debug("Прервано при загрузке страницы: {}", fullUrl);
                return;
            }
            log.warn("Не удалось загрузить страницу: {}", fullUrl);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("прервана")) {
                throw e;
            }
            log.warn("Ошибка обработки страницы {}: {}", fullUrl, e.getMessage());
        }
    }
    private void checkInterruption() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("Индексация прервана");
        }
    } private String getFullUrl() {
        String baseUrl = site.getUrl().endsWith("/") ?
                site.getUrl().substring(0, site.getUrl().length() - 1) : site.getUrl();
        String normalizedPath = path.equals("/") ? "" : path;
        return baseUrl + normalizedPath;
    }

    private void processSuccessfulPage(String fullUrl, String html) {
        checkInterruption();
        String textPreview = textExtractor.extract(html);
        log.info(" [{}} {}", Thread.currentThread().getName(),
                textPreview.length() > 100 ? textPreview.substring(0, 100) + "..." : textPreview);

        checkInterruption();

        Page page = createPage(html);
        Page savedPage = indexSaver.savePage(page);

        checkInterruption();

        String text = textExtractor.extract(html);
        Map<String, Integer> lemmas = lemmaFinder.getLemmas(text);

        indexSaver.saveLemmasAndIndex(savedPage, lemmas);

        if (depth < maxDepth) {
            checkInterruption();

            Set<String> links = extractInternalLinks(html);
            List<CrawlTask> subtasks = new ArrayList<>();

            for (String link : links) {
                checkInterruption();
                subtasks.add(new CrawlTask(site, link, depth + 1, visited, maxDepth,
                        pageFetcher, textExtractor, lemmaFinder, indexSaver));
            }

            checkInterruption();
            if (!subtasks.isEmpty()) {
                invokeAll(subtasks);
            }
        }
    }
    private Page createPage(String html) {
        Page page = new Page();
        page.setSite(site);
        page.setPath(path);
        page.setCode(200);
        page.setContent(html);
        return page;
    }

    private Set<String> extractInternalLinks(String html) {
        Set<String> links = new HashSet<>();
        try {
            Document doc = Jsoup.parse(html, site.getUrl());
            for (Element link : doc.select("a[href]")) {
                String href = link.absUrl("href");
                if (href.startsWith(site.getUrl())) {
                    String relativePath = extractRelativePath(href);
                    if (relativePath != null) {
                        links.add(relativePath);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка извлечения ссылок для {}", site.getUrl(), e);
        }
        return links;
    }

    private String extractRelativePath(String fullUrl) {
        String baseUrl = site.getUrl();
        if (fullUrl.startsWith(baseUrl)) {
            String path = fullUrl.substring(baseUrl.length());

            int anchorIndex = path.indexOf('#');
            if (anchorIndex != -1) {
                path = path.substring(0, anchorIndex);
            }

            int queryIndex = path.indexOf('?');
            if (queryIndex != -1) {
                path = path.substring(0, queryIndex);
            }

            if (path.isEmpty()) {
                return "/";
            }

            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            return path;
        }
        return null;
    }
}