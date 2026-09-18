package idusw.sbb.checkin.domain.route.fuel;

import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class FuelController {

    private final FuelService fuelService;

    // 싼 주유소 — 현재 위치 반경. 돌아갈 만큼 이득인 곳만 돌려준다.
    // fuelEff(연비 km/L), tankL(주유량) 안 주면 가정값(12, 40)으로 계산하고 표시한다.
    @GetMapping("/fuel")
    public ResponseEntity<ApiResponse<FuelService.FuelResult>> fuel(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Double fuelEff,
            @RequestParam(required = false) Integer tankL,
            @RequestParam(defaultValue = "5000") int radiusM) {
        return ResponseEntity.ok(ApiResponse.success(
                fuelService.recommend(lat, lng, fuelEff, tankL, radiusM)));
    }
}
