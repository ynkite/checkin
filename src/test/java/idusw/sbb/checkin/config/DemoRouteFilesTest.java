package idusw.sbb.checkin.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.route.RouteJson;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 시연 플랜 경로 파일이 시드 값과 어긋나지 않는지. 처음 파일은 하루 이름 자리에 구간 글이 들어가 있었다 */
class DemoRouteFilesTest {

    private static final Set<String> TYPES = Set.of("tour", "food", "cafe", "stay");

    private JsonNode read(String name) throws Exception {
        try (var in = new ClassPathResource("seed/demo-routes/" + name + ".json").getInputStream()) {
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(RouteJson.usable(s)).isTrue();
            assertThat(BudgetLoopSeedInitializer.hasLegAsDayLabel(s)).isFalse();
            return new ObjectMapper().readTree(s);
        }
    }

    private static long won(String sub) {
        return Long.parseLong(sub.substring(sub.indexOf('₩') + 1).replace(",", "").split("[^0-9]")[0]);
    }

    /* 일수 · 숙박 예측 · 식비 예측 — BudgetLoopSeedInitializer 의 seedTrip 값 */
    @ParameterizedTest
    @CsvSource({"gyeongju,2,120000,48000", "gangneung,3,240000,70000", "jeonju,2,90000,42000"})
    void 기간과_금액이_시드와_같고_하루_이름이_제대로_있다(String name, int days, long stay, long food) throws Exception {
        JsonNode r = read(name);
        assertThat(r.size()).isEqualTo(days);
        long staySum = 0, foodSum = 0;
        for (JsonNode d : r) {
            assertThat(d.path("label").asText()).startsWith("Day " + d.path("day").asInt() + " · ");
            for (JsonNode p : d.path("places")) {
                if (p.hasNonNull("transit")) continue;
                assertThat(TYPES).contains(p.path("type").asText());
                assertThat(p.path("lat").asDouble()).isNotZero();
                String sub = p.path("sub").asText();
                if ("stay".equals(p.path("type").asText())) staySum += won(sub);
                if (Set.of("food", "cafe").contains(p.path("type").asText())) foodSum += won(sub);
            }
        }
        assertThat(staySum).isEqualTo(stay);
        assertThat(foodSum).isEqualTo(food);
    }

    @Test
    void 결함_모양만_바로잡는다() {
        assertThat(BudgetLoopSeedInitializer.hasLegAsDayLabel(
                "[{\"day\":1,\"label\":\"🚗 자차 · 7.9km · 약 13분\",\"places\":[{\"name\":\"a\"}]}]")).isTrue();
        assertThat(BudgetLoopSeedInitializer.hasLegAsDayLabel(
                "[{\"day\":1,\"label\":\"Day 1 · 원도심에서 해운대까지\",\"places\":[{\"name\":\"a\"}]}]")).isFalse();
        assertThat(BudgetLoopSeedInitializer.hasLegAsDayLabel("{not json")).isFalse();
    }
}
