package idusw.sbb.checkin.global.controller;

import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.planshare.service.TripShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PageController {

    @Value("${kakao.maps.api.key}")
    private String kakaoMapKey;

    private final TripShareService tripShareService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("kakaoMapKey", kakaoMapKey);
        return "index";
    }

    // 읽기 전용 공유 링크 — 인증 없이 열린다. READER 권한, 편집 UI 는 프론트가 숨긴다.
    @GetMapping("/trip/{token}")
    public String sharedRead(@PathVariable String token, Model model) {
        model.addAttribute("kakaoMapKey", kakaoMapKey);
        try {
            TravelPlan plan = tripShareService.resolveByReadToken(token);
            model.addAttribute("tripId", plan.getId());
            model.addAttribute("shareRole", "READER");
            model.addAttribute("readonly", true);
        } catch (IllegalArgumentException e) {
            model.addAttribute("shareError", e.getMessage());
        }
        return "index";
    }

    // 편집 공유 링크 — EDITOR 권한. 읽기 토큰으로는 이 경로에 도달할 수 없다(토큰이 별개).
    @GetMapping("/trip/{token}/edit")
    public String sharedEdit(@PathVariable String token, Model model) {
        model.addAttribute("kakaoMapKey", kakaoMapKey);
        try {
            TravelPlan plan = tripShareService.resolveByEditToken(token);
            model.addAttribute("tripId", plan.getId());
            model.addAttribute("shareRole", "EDITOR");
            model.addAttribute("readonly", false);
        } catch (IllegalArgumentException e) {
            model.addAttribute("shareError", e.getMessage());
        }
        return "index";
    }
}
