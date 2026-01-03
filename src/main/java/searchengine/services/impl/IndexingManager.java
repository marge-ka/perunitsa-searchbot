package searchengine.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class IndexingManager {

    private volatile ExecutorService indexingExecutor;
    private final Map<Integer, Future<?>> siteFutures = new ConcurrentHashMap<>();
    private final Map<Integer, ForkJoinPool> forkJoinPools = new ConcurrentHashMap<>();
    private final AtomicBoolean isIndexingInProgress = new AtomicBoolean(false);
    private final AtomicInteger activeSitesCount = new AtomicInteger(0);

    private final Map<Integer, Thread> activeThreads = new ConcurrentHashMap<>();

    private synchronized ExecutorService getOrCreateExecutor() {
        if (indexingExecutor == null || indexingExecutor.isShutdown()) {
            indexingExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setName("Indexing-Thread-" + t.getId());
                return t;
            });
            log.info("Создан новый ExecutorService");
        }
        return indexingExecutor;
    }

    public boolean startIndexing() {
        log.info("Запрос на запуск индексации");
        boolean started = isIndexingInProgress.compareAndSet(false, true);
        if (started) {
            activeSitesCount.set(0);
            siteFutures.clear();
            forkJoinPools.clear();
            activeThreads.clear();
            log.info("Менеджер индексации запущен");
        }
        return started;
    }

    public void stopIndexing() {
        log.info("=== НАЧАЛО ПРИНУДИТЕЛЬНОЙ ОСТАНОВКИ ИНДЕКСАЦИИ ===");

        if (!isIndexingInProgress.get()) {
            log.warn("Индексация не запущена!");
            return;
        }

        log.info("1. Останавливаем {} ForkJoinPool...", forkJoinPools.size());
        forkJoinPools.forEach((siteId, pool) -> {
            if (pool != null && !pool.isShutdown()) {
                log.info("Принудительно останавливаем ForkJoinPool для сайта ID: {}", siteId);
                pool.shutdownNow();
                pool.getPoolSize();

                try {
                    if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                        log.warn("ForkJoinPool для сайта ID: {} не остановился, форсируем...", siteId);
                    }
                } catch (InterruptedException e) {
                    log.warn("Прервано ожидание ForkJoinPool");
                    Thread.currentThread().interrupt();
                }
            }
        });

        log.info("2. Отменяем {} задач...", siteFutures.size());
        siteFutures.forEach((siteId, future) -> {
            if (future != null && !future.isDone()) {
                log.info("Отменяем задачу для сайта ID: {}", siteId);
                boolean cancelled = future.cancel(true);
                if (cancelled) {
                    log.info("Задача для сайта ID: {} успешно отменена", siteId);
                } else {
                    log.warn("Не удалось отменить задачу для сайта ID: {}", siteId);
                }
            }
        });

        log.info("3. Прерываем все активные потоки...");
        activeThreads.forEach((siteId, thread) -> {
            if (thread != null && thread.isAlive()) {
                log.info("Прерываем поток для сайта ID: {} - {}", siteId, thread.getName());
                thread.interrupt();
            }
        });

        log.info("4. Останавливаем ExecutorService...");
        if (indexingExecutor != null) {
            indexingExecutor.shutdownNow();
            try {
                if (!indexingExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("ExecutorService не остановился, создаем новый");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            indexingExecutor = null;
        }

        log.info("5. Очищаем коллекции...");
        siteFutures.clear();
        forkJoinPools.clear();
        activeThreads.clear();
        activeSitesCount.set(0);

        isIndexingInProgress.set(false);

        log.info("=== ИНДЕКСАЦИЯ ПРИНУДИТЕЛЬНО ОСТАНОВЛЕНА ===");
    }

    public void registerSiteTask(Integer siteId, Future<?> future) {
        siteFutures.put(siteId, future);
        int newCount = activeSitesCount.incrementAndGet();
        log.debug("Зарегистрирована задача для сайта ID: {}. Всего активных задач: {}", siteId, newCount);
    }

    public void registerForkJoinPool(Integer siteId, ForkJoinPool pool) {
        forkJoinPools.put(siteId, pool);
        log.debug("Зарегистрирован ForkJoinPool для сайта ID: {}. Всего пулов: {}", siteId, forkJoinPools.size());
    }

    public void registerActiveThread(Integer siteId, Thread thread) {
        activeThreads.put(siteId, thread);
        log.debug("Зарегистрирован поток для сайта ID: {} - {}", siteId, thread.getName());
    }

    public void unregisterSite(Integer siteId) {
        Future<?> removedFuture = siteFutures.remove(siteId);
        ForkJoinPool removedPool = forkJoinPools.remove(siteId);
        Thread removedThread = activeThreads.remove(siteId);

        if (removedFuture != null) {
            int newCount = activeSitesCount.decrementAndGet();
            log.debug("Удалена регистрация сайта ID: {}. Осталось активных задач: {}", siteId, newCount);
        }
        if (removedPool != null && !removedPool.isShutdown()) {
            removedPool.shutdownNow();
        }
        if (removedThread != null && removedThread.isAlive()) {
            removedThread.interrupt();
        }
    }

    public ExecutorService getIndexingExecutor() {
        return getOrCreateExecutor();
    }

    public boolean isIndexingInProgress() {
        return isIndexingInProgress.get();
    }

    public void completeIndexing() {
        log.info("Завершение индексации. Активных задач: {}", activeSitesCount.get());
        boolean allDone = siteFutures.values().stream()
                .allMatch(future -> future == null || future.isDone());

        if (allDone) {
            siteFutures.clear();
            forkJoinPools.clear();
            activeSitesCount.set(0);
            isIndexingInProgress.set(false);

            if (indexingExecutor != null) {
                indexingExecutor.shutdown();
            }
            log.info("Индексация полностью завершена и очищена");
        } else {
            log.warn("Не все задачи завершены. Ожидание завершения...");
        }
    }

    public int getActiveSitesCount() {
        return activeSitesCount.get();
    }

    public Map<Integer, Future<?>> getSiteFutures() {
        return new ConcurrentHashMap<>(siteFutures);
    }

    public int getForkJoinPoolsCount() {
        return forkJoinPools.size();
    }
}
