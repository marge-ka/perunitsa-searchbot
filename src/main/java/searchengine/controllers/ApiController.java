package searchengine.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.ResponseDTO;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;
import searchengine.services.impl.IndexingServiceImpl;
import searchengine.services.impl.IndexingManager;
import searchengine.services.impl.SearchServiceImpl;
import searchengine.services.impl.SinglePageIndexer;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingServiceImpl indexingServiceImpl;
    private final IndexingService indexingService;
    private final IndexingManager indexingManager;
    private final SinglePageIndexer singlePageIndexer;
    private final SearchServiceImpl searchService;

    public ApiController(
            StatisticsService statisticsService,
            IndexingServiceImpl indexingServiceImpl,
            IndexingService indexingService,
            IndexingManager indexingManager,
            SinglePageIndexer singlePageIndexer,
            SearchServiceImpl searchService) {
        this.statisticsService = statisticsService;
        this.indexingServiceImpl = indexingServiceImpl;
        this.indexingService = indexingService;
        this.indexingManager = indexingManager;
        this.singlePageIndexer = singlePageIndexer;
        this.searchService = searchService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ResponseDTO> startIndexing() {
        ResponseDTO response = indexingServiceImpl.startIndexingWithResponse();
        if (response.isResult()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ResponseDTO> stopIndexing() {
        ResponseDTO response = indexingServiceImpl.stopIndexingWithResponse();
        if (response.isResult()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        StatisticsResponse response = statisticsService.getStatistics();
        if (response.isResult()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new SearchResponse(false, "Задан пустой поисковый запрос"));
        }
        if (offset < 0) {
            return ResponseEntity.badRequest()
                    .body(new SearchResponse(false, "offset не может быть отрицательным"));
        }
        if (limit <= 0 || limit > 100) {
            return ResponseEntity.badRequest()
                    .body(new SearchResponse(false, "limit должен быть от 1 до 100"));
        }

        SearchResponse response = searchService.search(query, site, offset, limit);
        if (response.isResult()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @PostMapping(value = "/indexPage", consumes = {"*/*"})
    public ResponseEntity<ResponseDTO> indexPage(
            HttpServletRequest request,
            @RequestParam(value = "page", required = false) String pageParam,
            @RequestParam(value = "url", required = false) String urlParam) {

        try {
            String url = extractUrlFromRequest(request, pageParam, urlParam);
            if (url == null || url.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ResponseDTO(false, "URL не может быть пустым"));
            }

            String cleanedUrl = cleanAndValidateUrl(url);
            if (cleanedUrl == null) {
                return ResponseEntity.badRequest()
                        .body(new ResponseDTO(false, "Некорректный формат URL"));
            }

            if (!singlePageIndexer.isUrlValid(cleanedUrl)) {
                return ResponseEntity.badRequest()
                        .body(new ResponseDTO(false, "Страница вне списка сайтов из конфигурации"));
            }

            return executePageIndexing(cleanedUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDTO(false, "Внутренняя ошибка при индексации: " + e.getMessage()));
        }
    }

    private String extractUrlFromRequest(HttpServletRequest request, String pageParam, String urlParam) throws Exception {
        String url = pageParam != null ? pageParam.trim() : null;
        if (url == null && urlParam != null) {
            url = urlParam.trim();
        }
        if (url == null) {
            url = readRequestBody(request);
        }
        return url != null ? url.trim() : null;
    }

    private String readRequestBody(HttpServletRequest request) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString().isEmpty() ? null : parseUrlFromBody(body.toString(), request.getContentType());
    }

    private String parseUrlFromBody(String body, String contentType) {
        ObjectMapper mapper = new ObjectMapper();
        if (contentType != null && contentType.contains("application/json")) {
            try {
                Map<String, Object> json = mapper.readValue(body, Map.class);
                for (String key : Arrays.asList("url", "page", "link", "uri")) {
                    if (json.containsKey(key) && json.get(key) instanceof String) {
                        return ((String) json.get(key)).trim();
                    }
                }
                for (Object value : json.values()) {
                    if (value instanceof String) return ((String) value).trim();
                }
            } catch (Exception ignored) {}
        }
        return body.trim();
    }

    private String cleanAndValidateUrl(String url) {
        url = url.replaceAll("^\"|\"$", "").trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null;
        }
        return url;
    }

    private ResponseEntity<ResponseDTO> executePageIndexing(String url) {
        try {
            boolean success = singlePageIndexer.indexSinglePage(url);
            if (success) {
                return ResponseEntity.ok(new ResponseDTO(true));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseDTO(false, "Ошибка при индексации страницы"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDTO(false, "Внутренняя ошибка при индексации: " + e.getMessage()));
        }
    }
}