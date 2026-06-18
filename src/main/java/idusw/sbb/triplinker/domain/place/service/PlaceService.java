package idusw.sbb.triplinker.domain.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.triplinker.domain.place.entity.Place;
import idusw.sbb.triplinker.domain.place.entity.PlaceCategory;
import idusw.sbb.triplinker.domain.place.repository.PlaceRepository;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final TravelPlanRepository planRepository;
    private final ObjectMapper objectMapper;

    // 별점에서 숫자 추출
    private static final Pattern RATING_PATTERN = Pattern.compile("([\\d.]+)\\s*$");

    // "숙소 · ₩180,000" 에서 단가(×N 이전 금액) 추출
    private static final Pattern UNIT_PRICE_PATTERN = Pattern.compile("₩([\\d,]+)");

    /**
     * routeJson에서 장소를 파싱해 PLACES에 저장
     * 이미 등록된 장소는 재등록 하지 않음
     */
    @Transactional
    public void parseAndSavePlacesFromRouteJson(TravelPlan plan, String json) {
        if (json == null || json.isBlank()) return;

        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) return;

            for (JsonNode dayNode : root) {
                JsonNode places = dayNode.path("places");
                if (!places.isArray()) continue;

                for (JsonNode placeNode : places) {
                    if (!placeNode.has("type")) continue; // transit 항목 스킵

                    String name = placeNode.path("name").asText("").trim();
                    if (name.isEmpty()) continue;

                    String type = placeNode.path("type").asText("");
                    String stars = placeNode.path("stars").asText(null);
                    String sub   = placeNode.path("sub").asText(null);

                    findOrCreatePlace(name, type, stars, sub);
                }
            }
        } catch (Exception e) {
            System.err.println("[PlaceService] 장소 파싱 실패 (planId=" + plan.getId() + "): " + e.getMessage());
        }
    }

    /**
     * 전체 플랜을 대상으로 PLACES 테이블을 일괄 동기화
     */
    @Transactional
    public void parseAndSavePlacesFromAllPlans() {
        // TODO: 확정 기능 구현 후 아래 한 줄로 교체
        // List<TravelPlan> plans = planRepository.findByStatus("CONFIRMED");
        List<TravelPlan> plans = planRepository.findAll();

        for (TravelPlan plan : plans) {
            String json = plan.getRouteJson();
            if (json != null && !json.isBlank()) {
                parseAndSavePlacesFromRouteJson(plan, json);
            }
        }
    }

    /**
     * name 단독 기준으로 중복 판단
     * AI JSON에는 address, external_link가 포함되지 않으므로 이름으로만 중복 판단
     */
    @Transactional
    public Place findOrCreatePlace(String name, String type,
                                   String stars, String sub) {
        return findOrCreatePlace(name, type, stars, sub, null, null);
    }

    @Transactional
    public Place findOrCreatePlace(String name, String type,
                                   String stars, String sub,
                                   String address, String externalLink) {
        //external_link
        if (externalLink != null && !externalLink.isBlank()) {
            Optional<Place> byLink = placeRepository.findByExternalLink(externalLink);
            if (byLink.isPresent()) return byLink.get();
        }

        //name + address
        if (address != null && !address.isBlank()) {
            Optional<Place> byNameAddr = placeRepository.findByNameAndAddress(name, address);
            if (byNameAddr.isPresent()) return byNameAddr.get();
        }

        //name 단독 (AI JSON 파싱 경로)
        Optional<Place> byName = placeRepository.findByName(name);
        if (byName.isPresent()) return byName.get();

        // 신규 장소 INSERT
        PlaceCategory category = mapTypeToCategory(type);
        if (category == null) return null;

        return placeRepository.save(Place.builder()
                .name(name)
                .category(category)
                .address(address)
                .externalLink(externalLink)
                .externalRating(parseRatingFromStars(stars))
                .avgPrice(parseUnitPriceFromSub(sub))
                .build());
    }

    private PlaceCategory mapTypeToCategory(String type) {
        return switch (type.toLowerCase()) {
            case "stay"        -> PlaceCategory.ACCOMMODATION;
            case "food"          -> PlaceCategory.RESTAURANT;
            case "cafe"          -> PlaceCategory.CAFE;
            case "tour"        -> PlaceCategory.ATTRACTION;
            default            -> null;
        };
    }

    private BigDecimal parseRatingFromStars(String stars) {
        if (stars == null) return null;
        Matcher m = RATING_PATTERN.matcher(stars.trim());
        if (!m.find()) return null;
        try {
            return new BigDecimal(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseUnitPriceFromSub(String sub) {
        if (sub == null) return null;
        Matcher m = UNIT_PRICE_PATTERN.matcher(sub);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}