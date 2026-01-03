package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.extractor.TextExtractor;
import searchengine.fetcher.FetchResult;
import searchengine.fetcher.PageFetcher;
import searchengine.lemma.LemmaFinder;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.saver.IndexSaver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SinglePageIndexer {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final PageFetcher pageFetcher;
    private final TextExtractor textExtractor;
    private final LemmaFinder lemmaFinder;
    private final IndexSaver indexSaver;

    public boolean isUrlValid(String pageUrl) {
        try {
            URL url = new URL(pageUrl);
            String pageHost = url.getHost();

            for (SiteConfig siteConfig : sitesList.getSites()) {
                URL siteUrl = new URL(siteConfig.getUrl());
                if (siteUrl.getHost().equals(pageHost)) {
                    return true;
                }
            }
            return false;
        } catch (MalformedURLException e) {
            log.error("Некорректный URL: {}", pageUrl, e);
            return false;
        }
    }

    public Optional<SiteConfig> findSiteConfigForUrl(String pageUrl) {
        try {
            URL url = new URL(pageUrl);
            String pageHost = url.getHost();

            return sitesList.getSites().stream()
                    .filter(config -> {
                        try {
                            URL siteUrl = new URL(config.getUrl());
                            return siteUrl.getHost().equals(pageHost);
                        } catch (MalformedURLException e) {
                            return false;
                        }
                    })
                    .findFirst();
        } catch (MalformedURLException e) {
            log.error("Некорректный URL: {}", pageUrl, e);
            return Optional.empty();
        }
    }

    @Transactional
    public boolean indexSinglePage(String pageUrl) {
        log.info("Начало индексации отдельной страницы: {}", pageUrl);

        if (!isUrlValid(pageUrl)) {
            log.warn("Страница находится за пределами сайтов, указанных в конфигурации: {}", pageUrl);
            return false;
        }

        SiteConfig siteConfig = findSiteConfigForUrl(pageUrl)
                .orElseThrow(() -> new IllegalArgumentException("Сайт не найден в конфигурации"));

        try {
            Site site = getOrCreateSite(siteConfig);
            String relativePath = extractRelativePath(pageUrl, siteConfig.getUrl());
            FetchResult result = pageFetcher.fetch(pageUrl);

            if (result.getStatusCode() != 200) {
                log.error("Ошибка загрузки страницы. Статус код: {}", result.getStatusCode());
                updateSiteStatus(site, SiteStatus.FAILED, "Ошибка загрузки страницы: статус " + result.getStatusCode());
                return false;
            }
            Optional<Page> existingPage = pageRepository.findBySiteAndPath(site, relativePath);

            if (existingPage.isPresent()) {
                deletePageData(existingPage.get());
                log.info("Удалены старые данные страницы: {}", pageUrl);
            }
            Page page = new Page();
            page.setSite(site);
            page.setPath(relativePath);
            page.setCode(result.getStatusCode());
            page.setContent(result.getHtml());

            Page savedPage = indexSaver.savePage(page);

            String text = textExtractor.extract(result.getHtml());
            Map<String, Integer> lemmas = lemmaFinder.getLemmas(text);

            indexSaver.saveLemmasAndIndex(savedPage, lemmas);
            updateSiteStatus(site, SiteStatus.INDEXED, null);

            log.info("Страница успешно проиндексирована: {}", pageUrl);
            return true;

        } catch (IOException e) {
            log.error("Ошибка загрузки страницы: {}", pageUrl, e);
            return false;
        } catch (Exception e) {
            log.error("Ошибка при индексации страницы: {}", pageUrl, e);
            return false;
        }
    }

    private String extractRelativePath(String pageUrl, String siteUrl) {
        try {
            URL fullUrl = new URL(pageUrl);
            URL baseUrl = new URL(siteUrl);

            String path = fullUrl.getPath();
            String query = fullUrl.getQuery();

            String relativePath = path.isEmpty() ? "/" : path;

            if (query != null && !query.isEmpty()) {
                relativePath += "?" + query;
            }

            return relativePath;
        } catch (MalformedURLException e) {
            log.error("Ошибка извлечения относительного пути", e);
            return "/";
        }
    }

    private Site getOrCreateSite(SiteConfig siteConfig) {
        return siteRepository.findByUrl(siteConfig.getUrl())
                .orElseGet(() -> {
                    Site newSite = new Site();
                    newSite.setUrl(siteConfig.getUrl());
                    newSite.setName(siteConfig.getName());
                    newSite.setStatus(SiteStatus.INDEXING);
                    newSite.setStatusTime(LocalDateTime.now());
                    newSite.setLastError(null);
                    return siteRepository.save(newSite);
                });
    }

    private void updateSiteStatus(Site site, SiteStatus status, String error) {
        site.setStatus(status);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(error);
        siteRepository.save(site);
    }

    private void deletePageData(Page page) {
        indexSaver.deletePageData(page);
    }
}