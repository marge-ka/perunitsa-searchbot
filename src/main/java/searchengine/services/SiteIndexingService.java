package searchengine.services;

import searchengine.model.Site;

public interface SiteIndexingService {
    void indexSingleSite(Site site);
}