package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

public interface IndexRepository extends JpaRepository<Index, Integer> {
    @Modifying
    @Query("DELETE FROM Index i WHERE i.page = :page")
    int deleteByPage(@Param("page") Page page);

    List<Index> findAllByLemma(Lemma lemma);

    @Query("SELECT i FROM Index i WHERE i.page = :page AND i.lemma = :lemma")
    Index findByPageAndLemma(@Param("page") Page page, @Param("lemma") Lemma lemma);

    @Query("SELECT i FROM Index i WHERE i.page IN :pages")
    List<Index> findAllByPages(@Param("pages") List<Page> pages);

}