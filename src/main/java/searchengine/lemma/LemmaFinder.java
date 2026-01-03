package searchengine.lemma;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class LemmaFinder {

    private final LuceneMorphology morphology;
    private static final Pattern WORD_PATTERN = Pattern.compile("[а-яёa-z]+");
    private static final Set<String> SERVICE_PARTS = Set.of("CONJ", "PREP", "PRCL", "INTJ");

    public LemmaFinder() {
        try {
            this.morphology = new RussianLuceneMorphology();
            log.info("✅ RussianLuceneMorphology успешно инициализирован");
        } catch (IOException e) {
            log.error("❌ Не удалось инициализировать морфологию", e);
            throw new IllegalStateException("Ошибка инициализации RussianLuceneMorphology", e);
        }
    }

    public Map<String, Integer> getLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() < 3) continue;

            try {
                List<String> normalForms = morphology.getNormalForms(word);
                if (normalForms.isEmpty()) continue;

                String lemma = normalForms.get(0);
                List<String> morphInfo = morphology.getMorphInfo(word);
                boolean isServiceWord = morphInfo.stream()
                        .anyMatch(info -> {
                            for (String tag : SERVICE_PARTS) {
                                if (info.contains(tag)) return true;
                            }
                            return false;
                        });

                if (!isServiceWord) {
                    lemmas.merge(lemma, 1, Integer::sum);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return lemmas;
    }
}
