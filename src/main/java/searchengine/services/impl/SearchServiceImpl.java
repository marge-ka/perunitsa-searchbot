package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResultItem;
import searchengine.lemma.LemmaFinder;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl {

    private static final double MAX_LEMMA_FREQUENCY_RATIO = 0.7;

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaFinder lemmaFinder;


    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        try {
            log.info("Поиск запроса: '{}', сайт: {}, offset: {}, limit: {}",
                    query, siteUrl, offset, limit);

            if (query == null || query.trim().isEmpty()) {
                return new SearchResponse(false, "Задан пустой поисковый запрос");
            }

            if (pageRepository.count() == 0) {
                return new SearchResponse(false, "Индекс пуст. Сначала проиндексируйте сайты.");
            }

            List<Site> sites = getSitesForSearch(siteUrl);
            if (sites.isEmpty()) {
                return new SearchResponse(false, "Нет проиндексированных сайтов для поиска");
            }

            if (sites.isEmpty()) {
                String errorMessage;
                if (siteUrl != null && !siteUrl.trim().isEmpty()) {
                    errorMessage = String.format(
                            "Сайт '%s' не проиндексирован. Запустите индексацию этого сайта.",
                            siteUrl
                    );
                } else {
                    errorMessage = "Нет проиндексированных сайтов для поиска. " +
                            "Запустите полную индексацию или индексацию отдельных сайтов.";
                }
                return new SearchResponse(false, errorMessage);
            }

            Set<String> queryLemmas = getLemmasFromQuery(query);
            if (queryLemmas.isEmpty()) {
                return new SearchResponse(false, "Поисковый запрос не содержит значимых слов");
            }

            List<Page> relevantPages = findRelevantPages(sites, queryLemmas);
            if (relevantPages.isEmpty()) {
                return new SearchResponse(false, "По запросу ничего не найдено");
            }

            List<SearchResultItem> searchResults = calculateRelevanceAndFormat(
                    relevantPages, query, sites, offset, limit
            );

            return new SearchResponse(true, relevantPages.size(), searchResults);

        } catch (Exception e) {
            log.error("Ошибка при поиске: ", e);
            return new SearchResponse(false, "Внутренняя ошибка при выполнении поиска");
        }
    }

    private List<Site> getSitesForSearch(String siteUrl) {
        if (siteUrl != null && !siteUrl.trim().isEmpty()) {
            Optional<Site> site = siteRepository.findByUrl(siteUrl);
            return site.filter(s -> s.getStatus() == SiteStatus.INDEXED)
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());
        } else {
            return siteRepository.findAll().stream()
                    .filter(s -> s.getStatus() == SiteStatus.INDEXED)
                    .collect(Collectors.toList());
        }
    }

    private Set<String> getLemmasFromQuery(String query) {
        Map<String, Integer> lemmasMap = lemmaFinder.getLemmas(query);
        return lemmasMap.keySet();
    }

    private List<Page> findRelevantPages(List<Site> sites, Set<String> rawQueryLemmas) {
        Map<String, Long> lemmaGlobalFrequencies = new HashMap<>();
        for (String lemma : rawQueryLemmas) {
            long totalFreq = sites.stream()
                    .map(site -> {
                        Lemma l = lemmaRepository.findBySiteAndLemma(site, lemma);
                        return l != null ? l.getFrequency() : 0L;
                    })
                    .mapToLong(Long::longValue)
                    .sum();
            lemmaGlobalFrequencies.put(lemma, totalFreq);
        }

        long totalPages = pageRepository.count();
        long maxAllowedFrequency = (long) (totalPages * MAX_LEMMA_FREQUENCY_RATIO);

        List<String> filteredLemmas = rawQueryLemmas.stream()
                .filter(lemma -> lemmaGlobalFrequencies.getOrDefault(lemma, 0L) <= maxAllowedFrequency)
                .collect(Collectors.toList());

        if (filteredLemmas.isEmpty()) {
            return Collections.emptyList();
        }

        filteredLemmas.sort(Comparator.comparingLong(lemma -> lemmaGlobalFrequencies.getOrDefault(lemma, 0L)));

        Set<Page> pages = null;
        for (String lemma : filteredLemmas) {
            Set<Page> currentLemmaPages = new HashSet<>();
            for (Site site : sites) {
                Lemma lemmaEntity = lemmaRepository.findBySiteAndLemma(site, lemma);
                if (lemmaEntity != null) {
                    List<Index> indices = indexRepository.findAllByLemma(lemmaEntity);
                    indices.stream()
                            .map(Index::getPage)
                            .forEach(currentLemmaPages::add);
                }
            }

            if (pages == null) {
                pages = currentLemmaPages;
            } else {
                pages.retainAll(currentLemmaPages);
            }

            if (pages.isEmpty()) break;
        }

        return pages != null ? new ArrayList<>(pages) : Collections.emptyList();
    }

    private List<SearchResultItem> calculateRelevanceAndFormat(
            List<Page> pages, String query, List<Site> sites, int offset, int limit) {

        Map<Page, Double> pageRelevance = calculateAbsoluteRelevance(pages, query);

        Map<Page, Double> normalizedRelevance = normalizeRelevance(pageRelevance);

        List<Page> sortedPages = pages.stream()
                .sorted((p1, p2) -> Double.compare(
                        normalizedRelevance.getOrDefault(p2, 0.0),
                        normalizedRelevance.getOrDefault(p1, 0.0)
                ))
                .collect(Collectors.toList());

        List<Page> paginatedPages = sortedPages.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        List<SearchResultItem> results = new ArrayList<>();
        for (Page page : paginatedPages) {
            SearchResultItem item = new SearchResultItem();
            item.setSite(page.getSite().getUrl());
            item.setSiteName(page.getSite().getName());
            item.setUri(page.getPath());
            item.setTitle(extractTitle(page.getContent()));
            item.setSnippet(generateSnippet(page.getContent(), query));
            item.setRelevance(normalizedRelevance.getOrDefault(page, 0.0));
            results.add(item);
        }

        return results;
    }

    private Map<Page, Double> calculateAbsoluteRelevance(List<Page> pages, String query) {
        Map<Page, Double> relevanceMap = new HashMap<>();
        Set<String> queryLemmas = getLemmasFromQuery(query);

        for (Page page : pages) {
            double relevance = 0.0;

            for (String lemma : queryLemmas) {
                Lemma lemmaEntity = lemmaRepository.findBySiteAndLemma(page.getSite(), lemma);
                if (lemmaEntity != null) {
                    Index index = indexRepository.findByPageAndLemma(page, lemmaEntity);
                    if (index != null) {
                        double lemmaWeight = index.getRank();
                        relevance += lemmaWeight;
                    }
                }
            }
            relevanceMap.put(page, relevance);
        }

        return relevanceMap;
    }

    private Map<Page, Double> normalizeRelevance(Map<Page, Double> relevanceMap) {
        if (relevanceMap.isEmpty()) {
            return Collections.emptyMap();
        }
        double maxRelevance = Collections.max(relevanceMap.values());
        if (maxRelevance == 0) {
            return relevanceMap;
        }
        Map<Page, Double> normalized = new HashMap<>();
        for (Map.Entry<Page, Double> entry : relevanceMap.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / maxRelevance);
        }
        return normalized;
    }

    private String extractTitle(String html) {
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
            String title = doc.title();
            return title != null && !title.isEmpty() ? title : "Без заголовка";
        } catch (Exception e) {
            return "Без заголовка";
        }
    }

    private String generateSnippet(String html, String query) {
        try {
            String text = org.jsoup.Jsoup.parse(html).text();

            Set<String> queryWords = Arrays.stream(query.toLowerCase().split("\\s+"))
                    .filter(word -> word.length() > 2)
                    .collect(Collectors.toSet());

            int snippetStart = -1;
            for (String word : queryWords) {
                int pos = text.toLowerCase().indexOf(word);
                if (pos >= 0) {
                    snippetStart = Math.max(0, pos - 100);
                    break;
                }
            }
            if (snippetStart < 0) {
                snippetStart = 0;
            }
            int snippetEnd = Math.min(text.length(), snippetStart + 200);
            String snippet = text.substring(snippetStart, snippetEnd);

            for (String word : queryWords) {
                snippet = snippet.replaceAll("(?i)(" + Pattern.quote(word) + ")", "<b>$1</b>");
            }

            if (snippetStart > 0) {
                snippet = "..." + snippet;
            }
            if (snippetEnd < text.length()) {
                snippet = snippet + "...";
            }
            return snippet;
        } catch (Exception e) {
            return "Фрагмент текста не доступен";
        }
    }
}