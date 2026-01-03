package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Integer> {

    @Query("SELECT p FROM Page p WHERE p.site = :site AND p.path = :path ORDER BY p.id DESC")
    List<Page> findBySiteAndPathOrdered(@Param("site") Site site, @Param("path") String path);

    default Optional<Page> findBySiteAndPath(Site site, String path) {
        List<Page> pages = findBySiteAndPathOrdered(site, path);
        return pages.isEmpty() ? Optional.empty() : Optional.of(pages.get(0));
    }

    @Query("SELECT COUNT(p) FROM Page p WHERE p.site = :site")
    long countBySite(@Param("site") Site site);

    void deleteBySiteId(Integer siteId);
}