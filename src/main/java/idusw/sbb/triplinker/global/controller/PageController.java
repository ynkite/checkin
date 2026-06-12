package idusw.sbb.triplinker.global.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @Value("${kakao.maps.api.key}")
    private String kakaoMapKey;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("kakaoMapKey", kakaoMapKey);
        return "index";
    }
}