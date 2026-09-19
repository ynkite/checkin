package idusw.sbb.checkin.domain.route.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「같은 날 같은 성격 두 번 금지」(작업지시 2번)의 판정 기준을 지킨다.
 *
 * <p>기존 규칙은 <b>동일 상호명</b>만 막아서 해운대와 광안리가 그대로 통과했다. 이름이 아니라
 * 카카오 업종으로 성격을 갈라야 걸린다.
 *
 * <p>제일 중요한 것은 <b>모르는 업종을 한 덩어리로 묶지 않는 것</b>이다. 묶으면 서로 무관한
 * 두 곳이 「겹친다」고 잘못 걸려 멀쩡한 장소가 교체된다.
 */
class RouteCategoryBucketTest {

    @Test
    void 해수욕장과_해변은_같은_성격이다() {
        // 해운대(해수욕장)와 광안리(해변) — 이름이 달라 기존 규칙은 못 걸렀다
        String a = AiRouteService.categoryBucket("여행 > 관광,명소 > 해수욕장");
        String b = AiRouteService.categoryBucket("여행 > 관광,명소 > 해변");

        assertThat(a).isEqualTo(b).isEqualTo("해변");
    }

    @Test
    void 공원끼리_같은_성격이다() {
        // 광주에서 「월산공원」과 「월산근린공원」이 한 날에 같이 들어왔다
        assertThat(AiRouteService.categoryBucket("여행 > 관광,명소 > 공원"))
                .isEqualTo(AiRouteService.categoryBucket("여행 > 관광,명소 > 유원지"))
                .isEqualTo("공원");
    }

    @Test
    void 성격이_다르면_안_겹친다() {
        assertThat(AiRouteService.categoryBucket("여행 > 관광,명소 > 해수욕장"))
                .isNotEqualTo(AiRouteService.categoryBucket("문화,예술 > 박물관,기념관"));
    }

    @Test
    void 모르는_업종은_한_덩어리로_묶지_않는다() {
        // null 끼리 묶으면 서로 무관한 두 곳이 「겹친다」고 잘못 걸린다
        assertThat(AiRouteService.categoryBucket("서비스,산업 > 미용")).isNull();
        assertThat(AiRouteService.categoryBucket("")).isNull();
        assertThat(AiRouteService.categoryBucket(null)).isNull();
    }

    @Test
    void 산책로를_산으로_읽지_않는다() {
        // 「해안산책로」에 '산' 이 들어 있다. 부분 문자열로 판정하면 산이 된다
        assertThat(AiRouteService.categoryBucket("여행 > 관광,명소 > 산책로")).isNotEqualTo("산");
        assertThat(AiRouteService.categoryBucket("여행 > 관광,명소 > 산")).isEqualTo("산");
    }
}
