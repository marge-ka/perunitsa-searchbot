package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchResultItem> data;
    private String error;

    public SearchResponse(boolean result, int count, List<SearchResultItem> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }

    public SearchResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }
}