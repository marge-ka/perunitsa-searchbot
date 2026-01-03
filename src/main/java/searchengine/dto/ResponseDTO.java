package searchengine.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO {
    private boolean result;
    private String error;

    public ResponseDTO(boolean result) {
        this.result = result;
    }
}