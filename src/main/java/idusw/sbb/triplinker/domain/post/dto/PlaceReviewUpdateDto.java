package idusw.sbb.triplinker.domain.post.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class PlaceReviewUpdateDto {
    private List<PlaceReviewUpdateItem> reviews;

    @Getter
    public static class PlaceReviewUpdateItem {
        private Long   placeReviewId;
        private int    rating;
        private String comment;
    }
}