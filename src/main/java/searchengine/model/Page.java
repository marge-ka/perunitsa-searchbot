package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.persistence.Index;

@Setter
@Getter
@Entity
@Table(name = "page", indexes = {@Index(name = "idx_path", columnList = "path")})
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false)
    private Integer code;

    @Column(length = 512, nullable = false)
    private String path;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}