package idusw.sbb.checkin.domain.live;

import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 실시간 화면이 부르는 자리.
 *
 * 왜 따로 두는가 — 지도 화면은 「계획을 고친다」이고 이 화면은
 * 「지금 어떤가」다. 지도는 전체 일정을 펼쳐 놓고 손으로 옮기는 자리라
 * 운전 중에 쓸 수 없다. 실시간은 지금 한 구간만 크게 보여 준다.
 *
 * 여행 전날에도 들어올 수 있다. 그때는 「내일 이 시각이면」으로 답한다.
 */
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveController {

    private final LiveService live;

    /** 실시간으로 열 수 있는 여행 목록. 지난 여행은 뺀다 */
    @GetMapping("/trips")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> trips() {
        return ResponseEntity.ok(ApiResponse.success(live.openableTrips()));
    }

    /**
     * 지금 상황 한 벌.
     *
     * @param tripId 여행
     * @param lat,lng 지금 위치. 없으면 첫 장소 기준으로 답한다
     * @param at     기준 시각. 없으면 지금. 전날 미리 볼 때 쓴다
     */
    @GetMapping("/now")
    public ResponseEntity<ApiResponse<LiveSnapshot>> now(
            @RequestParam Long tripId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(live.snapshot(tripId, lat, lng, date)));
    }
}
