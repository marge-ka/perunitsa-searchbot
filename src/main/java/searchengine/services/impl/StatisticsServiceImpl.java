package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    private final IndexingService indexingService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        log.debug("Получение статистики. Индексация активна: {}", indexingService.isIndexing());

        try {
            TotalStatistics total = new TotalStatistics();
            long sitesCount = siteRepository.count();
            long pagesCount = pageRepository.count();
            long lemmasCount = lemmaRepository.count();

            total.setSites((int) sitesCount);
            total.setPages((int) pagesCount);
            total.setLemmas((int) lemmasCount);
            total.setIndexing(indexingService.isIndexing());

            List<DetailedStatisticsItem> detailed = siteRepository.findAll().stream()
                    .map(site -> {
                        DetailedStatisticsItem item = new DetailedStatisticsItem();
                        item.setUrl(site.getUrl());
                        item.setName(site.getName());
                        item.setStatus(site.getStatus().toString());

                        if (site.getStatusTime() != null) {
                            item.setStatusTime(site.getStatusTime().toInstant(ZoneOffset.UTC).toEpochMilli());
                        } else {
                            item.setStatusTime(0L);
                        }

                        item.setError(site.getLastError());

                        int sitePages = (int) pageRepository.countBySite(site);
                        int siteLemmas = (int) lemmaRepository.countBySite(site);

                        item.setPages(sitePages);
                        item.setLemmas(siteLemmas);

                        return item;
                    })
                    .collect(Collectors.toList());

            StatisticsData data = new StatisticsData();
            data.setTotal(total);
            data.setDetailed(detailed);

            return new StatisticsResponse(true, data);

        } catch (Exception e) {
            log.error("Ошибка при получении статистики", e);
            return new StatisticsResponse(false, null);
        }
    }
}