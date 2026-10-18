package idusw.sbb.checkin.domain.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 끼니 시간의 먹거리 거리가 관광지 ₩0 이면 식당으로 돌려 끼니값을 매긴다 (54번 여행 저녁 0원). */
class MealGapTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void 저녁_시간에_식당이_없고_먹거리_골목이_0원이면_끼니값을_매긴다() throws Exception {
        String json = """
            [{"day":1,"places":[
               {"type":"food","name":"밀면집","sub":"맛집 · 점심 · ₩9,000×4","time":"12:00"},
               {"transit":"이동"},
               {"type":"tour","name":"해운대해수욕장","sub":"관광지 · ₩0×4","time":"16:00"},
               {"transit":"이동"},
               {"type":"tour","name":"해운대곰장어골목","sub":"관광지 · 1h · ₩0×4","time":"18:00"}]}]
            """;
        JsonNode places = OM.readTree(MealGap.fill(json, 4, 12_000)).get(0).path("places");

        assertEquals("tour", places.get(2).path("type").asText(), "끼니 시간이 아니면 그대로");
        JsonNode dinner = places.get(4);
        assertEquals("food", dinner.path("type").asText());
        assertEquals(48_000, BudgetFitService.amount(dinner.path("sub").asText()));
        assertTrue(dinner.path("sub").asText().contains("저녁"));
    }

    @Test
    void 그_끼니에_식당이_있거나_이름이_먹거리가_아니면_손대지_않는다() {
        String withFood = """
            [{"day":1,"places":[
               {"type":"tour","name":"자갈치시장","sub":"관광지 · ₩0","time":"18:00"},
               {"type":"food","name":"곰장어집","sub":"맛집 · 저녁 · ₩20,000×2","time":"19:00"}]}]
            """;
        String beach = """
            [{"day":1,"places":[{"type":"tour","name":"해운대해수욕장","sub":"관광지 · 저녁 산책","time":"18:00"}]}]
            """;
        assertSame(withFood, MealGap.fill(withFood, 2, 12_000));
        assertSame(beach, MealGap.fill(beach, 2, 12_000));
    }
}
