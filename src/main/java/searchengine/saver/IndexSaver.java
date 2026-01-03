package searchengine.saver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexSaver {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;

    @Transactional
    public Page savePage(Page page) {
        return pageRepository.save(page);
    }

    @Transactional
    public void saveLemmasAndIndex(Page page, Map<String, Integer> lemmas) {
        Site site = page.getSite();

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                log.debug("Прервано сохранение лемм для страницы {}", page.getId());
                throw new RuntimeException("Индексация прервана");
            }

            try {
                saveSingleLemmaWithRetry(page, site, entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.debug("Пропускаем лемму {} из-за ошибки: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void saveSingleLemmaWithRetry(Page page, Site site, String lemmaStr, int countOnPage) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                saveSingleLemma(page, site, lemmaStr, countOnPage);
                return;
            } catch (DataIntegrityViolationException e) {
                if (attempt == 2) {
                    throw e;
                }
                try {
                    Thread.sleep(10 * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Индексация прервана", ie);
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveSingleLemma(Page page, Site site, String lemmaStr, int countOnPage) {
        Lemma lemma = lemmaRepository.findBySiteAndLemma(site, lemmaStr);

        if (lemma == null) {
            lemma = new Lemma();
            lemma.setSite(site);
            lemma.setLemma(lemmaStr);
            lemma.setFrequency(countOnPage);
        } else {
            lemma.setFrequency(lemma.getFrequency() + countOnPage);
        }

        lemma = lemmaRepository.saveAndFlush(lemma);

        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("Индексация прервана");
        }

        Index indexEntry = new Index();
        indexEntry.setPage(page);
        indexEntry.setLemma(lemma);
        indexEntry.setRank((float) countOnPage);
        indexRepository.save(indexEntry);
    }

    @Transactional
    public void deletePageData(Page page) {
        try {
            int deletedIndexes = indexRepository.deleteByPage(page);
            log.debug("Удалено {} индексов для страницы ID: {}", deletedIndexes, page.getId());
        } catch (Exception e) {
            log.error("Ошибка при удалении данных страницы ID: {}", page.getId(), e);
            throw e;
        }
    }
}