package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Lemma findBySiteAndLemma(Site site, String lemma);
    void deleteBySiteId(Integer siteId);
    @Modifying
    @Query(value = "INSERT INTO lemma (site_id, lemma, frequency) " +
            "VALUES (:siteId, :lemma, :freq) " +
            "ON DUPLICATE KEY UPDATE frequency = frequency + :freq",
            nativeQuery = true)
    void upsertLemma(@Param("siteId") Integer siteId,
                     @Param("lemma") String lemma,
                     @Param("freq") Integer freq);
    @Query("SELECT COUNT(l) FROM Lemma l WHERE l.site = :site")
    long countBySite(@Param("site") Site site);
    @Modifying
    @Query("DELETE FROM Lemma l WHERE l.id IN " +
            "(SELECT i.lemma.id FROM Index i WHERE i.page = :page)")
    int deleteLemmasByPage(@Param("page") Page page);
}