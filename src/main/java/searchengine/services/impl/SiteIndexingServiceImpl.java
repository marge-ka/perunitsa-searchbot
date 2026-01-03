package searchengine.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import searchengine.crawler.PageCrawler;
import searchengine.model.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.SiteIndexingService;

import java.time.LocalDateTime;

@Service
@Slf4j
public class SiteIndexingServiceImpl implements SiteIndexingService {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private PageCrawler pageCrawler;

    @Autowired
    private IndexingManager indexingManager;

    @Override
    @Transactional(noRollbackFor = {PageCrawler.IndexingStoppedException.class})
    public void indexSingleSite(Site siteInput) {
        Site existing = siteRepository.findByUrl(siteInput.getUrl()).orElse(null);
        Site site;

        if (existing != null) {
            site = existing;
            if (site.getId() != null) {
                pageRepository.deleteBySiteId(site.getId());
                lemmaRepository.deleteBySiteId(site.getId());
            }
        } else {
            site = new Site();
            site.setUrl(siteInput.getUrl());
            site.setName(siteInput.getName());
            site.setStatus(SiteStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            site = siteRepository.save(site);
        }

        try {
            site.setStatus(SiteStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);
            pageCrawler.crawl(site);

            site.setStatus(SiteStatus.INDEXED);
            site.setLastError(null);

        } catch (PageCrawler.IndexingStoppedException e) {
            site.setStatus(SiteStatus.FAILED);
            site.setLastError("Индексация остановлена пользователем");
            log.info("Индексация сайта {} остановлена пользователем", site.getUrl());

        } catch (Exception e) {
            site.setStatus(SiteStatus.FAILED);
            site.setLastError(e.getMessage());
            log.error("Ошибка индексации сайта: " + site.getUrl(), e);
            if (!(e.getCause() instanceof TransactionException)) {
                throw e;
            }
        } finally {
            try {
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                indexingManager.unregisterSite(site.getId());
            } catch (Exception e) {
                log.error("Ошибка при финализации сайта {}", site.getUrl(), e);
            }
        }
    }
}