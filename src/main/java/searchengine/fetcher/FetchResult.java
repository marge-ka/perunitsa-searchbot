package searchengine.fetcher;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FetchResult {
    private final int statusCode;
    private final String html;
}