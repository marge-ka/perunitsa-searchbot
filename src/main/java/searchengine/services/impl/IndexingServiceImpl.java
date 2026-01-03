package searchengine.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.ResponseDTO;
import searchengine.model.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    @Autowired
    private IndexingOrchestrator orchestrator;

    @Autowired
    private IndexingManager indexingManager;

    @Autowired
    private SiteRepository siteRepository;

    private volatile Thread indexingThread;
    private final Object lock = new Object();

    @Override
    public boolean startIndexing() {
        synchronized (lock) {
            log.info("=== ЗАПРОС НА ЗАПУСК ИНДЕКСАЦИИ ===");

            if (indexingManager.isIndexingInProgress()) {
                log.warn("Индексация уже выполняется!");
                return false;
            }

            if (!indexingManager.startIndexing()) {
                log.warn("Не удалось запустить менеджер индексации");
                return false;
            }

            indexingThread = new Thread(() -> {
                log.info("Поток индексации запущен: {}", Thread.currentThread().getName());
                try {
                    orchestrator.startAsyncIndexing();
                    log.info("Оркестратор завершил работу");
                } catch (Exception e) {
                    log.error("Критическая ошибка при индексации", e);
                } finally {
                    indexingManager.completeIndexing();
                    log.info("Поток индексации завершен");
                }
            });

            indexingThread.setName("Indexing-Main-Thread");
            indexingThread.start();

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("=== ИНДЕКСАЦИЯ ЗАПУЩЕНА УСПЕШНО ===");
            return true;
        }
    }

    @Override
    public boolean stopIndexing() {
        synchronized (lock) {
            log.info("=== ЗАПРОС НА ОСТАНОВКУ ИНДЕКСАЦИИ ===");

            if (!indexingManager.isIndexingInProgress()) {
                log.warn("Индексация не запущена!");
                return false;
            }

            try {
                log.info("1. Останавливаем менеджер индексации...");
                indexingManager.stopIndexing();

                log.info("2. Прерываем основной поток индексации...");
                if (indexingThread != null && indexingThread.isAlive()) {
                    indexingThread.interrupt();

                    try {
                        indexingThread.join(3000);
                        if (indexingThread.isAlive()) {
                            log.warn("Поток индексации не завершился вовремя");
                        } else {
                            log.info("Поток индексации успешно завершен");
                        }
                    } catch (InterruptedException e) {
                        log.warn("Ожидание потока прервано");
                        Thread.currentThread().interrupt();
                    }
                }

                log.info("3. Обновляем статусы сайтов в БД...");
                updateFailedSitesStatus();

                log.info("4. Ожидаем завершения всех операций...");
                TimeUnit.SECONDS.sleep(1);

                log.info("=== ИНДЕКСАЦИЯ ОСТАНОВЛЕНА УСПЕШНО ===");
                return true;

            } catch (Exception e) {
                log.error("Ошибка при остановке индексации", e);
                return false;
            } finally {
                indexingThread = null;
            }
        }
    }

    private void updateFailedSitesStatus() {
        try {
            List<Site> indexingSites = siteRepository.findAllByStatus(SiteStatus.INDEXING);
            log.info("Найдено {} сайтов в статусе INDEXING", indexingSites.size());

            for (Site site : indexingSites) {
                log.info("Обновляю статус сайта: {} (ID: {})", site.getUrl(), site.getId());
                site.setStatus(SiteStatus.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }

            log.info("Статусы всех сайтов обновлены");
        } catch (Exception e) {
            log.error("Ошибка при обновлении статусов сайтов", e);
        }
    }

    @Override
    public boolean isIndexing() {
        boolean active = indexingManager.isIndexingInProgress();
        log.debug("Проверка статуса индексации: {}", active);
        return active;
    }

    public ResponseDTO startIndexingWithResponse() {
        log.info("API: /startIndexing");
        if (startIndexing()) {
            return new ResponseDTO(true);
        } else {
            return new ResponseDTO(false, "Индексация уже запущена");
        }
    }

    public ResponseDTO stopIndexingWithResponse() {
        log.info("API: /stopIndexing");
        if (stopIndexing()) {
            return new ResponseDTO(true);
        } else {
            return new ResponseDTO(false, "Индексация не запущена");
        }
    }
}
