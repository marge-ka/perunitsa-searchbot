package searchengine.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import searchengine.model.Site;
import searchengine.model.enums.SiteStatus;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class InitialDataLoader implements ApplicationRunner {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (siteRepository.count() == 0) {
            log.info("Таблица 'site' пуста. Загружаем сайты из application.yaml...");

            sitesList.getSites().forEach(config -> {
                Site site = new Site();
                site.setUrl(config.getUrl());
                site.setName(config.getName());
                site.setStatus(SiteStatus.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError(null);
                siteRepository.save(site);
            });

            log.info("Загружено {} сайтов из конфигурации.", sitesList.getSites().size());
        } else {
            log.info("Таблица 'site' уже содержит данные. Пропускаем инициализацию.");
        }
    }
}