package searchengine.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.SiteRepository;
import searchengine.services.SiteIndexingService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IndexingOrchestrator {

    @Autowired
    private SitesList sitesList;

    @Autowired
    private SiteIndexingService siteIndexingService;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private IndexingManager indexingManager;

    public void startAsyncIndexing() {
        List<SiteConfig> configs = sitesList.getSites();
        if (configs == null || configs.isEmpty()) {
            log.warn("Нет сайтов в конфигурации indexing-settings.sites");
            return;
        }

        List<Site> sitesToIndex = configs.stream()
                .map(this::prepareSiteForIndexing)
                .collect(Collectors.toList());

        log.info("Запуск индексации {} сайтов", configs.size());

        for (Site site : sitesToIndex) {
            Future<?> future = indexingManager.getIndexingExecutor().submit(() -> {
                indexingManager.registerActiveThread(site.getId(), Thread.currentThread());

                try {
                    log.info("Начата индексация сайта: {}", site.getUrl());
                    siteIndexingService.indexSingleSite(site);
                    log.info("Завершена индексация сайта: {}", site.getUrl());
                } catch (Exception e) {
                    log.error("Ошибка индексации сайта: {}", site.getUrl(), e);
                } finally {
                    indexingManager.unregisterSite(site.getId());
                }
            });

            indexingManager.registerSiteTask(site.getId(), future);
        }
    }

    private Site prepareSiteForIndexing(SiteConfig config) {
        Site site = siteRepository.findByUrl(config.getUrl()).orElse(null);

        if (site == null) {
            site = new Site();
            site.setUrl(config.getUrl());
            site.setName(config.getName());
        }

        site.setStatus(SiteStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(null);

        return siteRepository.save(site);
    }
}