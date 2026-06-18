package idusw.sbb.triplinker.domain.post.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class PlaceReviewSaveDto {
    private List<PlaceReviewItem> reviews;

    @Getter
    public static class PlaceReviewItem {
        private String placeName;
        private String placeType;
        private int rating;
        private String comment;
    }
}