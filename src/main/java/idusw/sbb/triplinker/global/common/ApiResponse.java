package idusw.sbb.triplinker.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    //성공
    public static <T> ApiResponse<T> success(String  message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    //실패
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
