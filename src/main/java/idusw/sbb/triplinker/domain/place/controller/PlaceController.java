package idusw.sbb.triplinker.domain.place.controller;

import idusw.sbb.triplinker.domain.place.dto.PlaceCardDto;
import idusw.sbb.triplinker.domain.place.entity.PlaceCategory;
import idusw.sbb.triplinker.domain.post.dto.PlaceReviewResponseDto;
import idusw.sbb.triplinker.domain.post.dto.PostListResponseDto;
import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.repository.PlaceReviewRepository;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceReviewRepository placeReviewRepository;

    // 카테고리별 장소 목록
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlaceCardDto>>> getPlaceCards(
            @RequestParam(defaultValue = "stay") String category,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size
    ) {
        PlaceCategory cat = toCategory(category);
        List<PlaceCardDto> result = placeReviewRepository
                .findPlaceCardsByCategory(cat, PageRequest.of(page, size))
                .stream()
                .map(PlaceCardDto::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("장소 목록 조회 성공", result));
    }

    // 특정 장소를 리뷰한 후기 목록
    @GetMapping("/{placeId}/posts")
    public ResponseEntity<ApiResponse<List<PostListResponseDto>>> getPostsByPlace(
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        List<Post> posts = placeReviewRepository.findPostsByPlaceId(placeId, PageRequest.of(page, size));
        List<PostListResponseDto> result = posts.stream()
                .map(PostListResponseDto::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("후기 목록 조회 성공", result));
    }

    // 특정 장소의 리뷰 목록 (별점 + 한줄평 + 후기 제목)
    @Transactional(readOnly = true)
    @GetMapping("/{placeId}/reviews")
    public ResponseEntity<ApiResponse<List<PlaceReviewResponseDto>>> getReviewsByPlace(
            @PathVariable Long placeId
    ) {
        List<PlaceReviewResponseDto> result = placeReviewRepository
                .findByPlace_IdOrderByCreatedAtDesc(placeId)
                .stream()
                .map(PlaceReviewResponseDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("리뷰 조회 성공", result));
    }

    private PlaceCategory toCategory(String s) {
        return switch (s.toLowerCase()) {
            case "stay" -> PlaceCategory.ACCOMMODATION;
            case "food" -> PlaceCategory.RESTAURANT;
            case "cafe" -> PlaceCategory.CAFE;
            default     -> PlaceCategory.ATTRACTION;
        };
    }
}