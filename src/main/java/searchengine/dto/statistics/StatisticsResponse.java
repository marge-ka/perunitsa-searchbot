package searchengine.dto.statistics;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;

    public StatisticsResponse(boolean result, StatisticsData statistics) {
        this.result = result;
        this.statistics = statistics;
    }
}
