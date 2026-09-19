package idusw.sbb.checkin.domain.tour.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.tour.service.OriginSearchService.Status;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

class OriginSearchServiceTest {

    private final RestTemplate rt = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();

    private OriginSearchService svc(String key) {
        OriginSearchService s = new OriginSearchService(rt, new ObjectMapper());
        s.setKakaoKey(key);
        return s;
    }

    @Test
    void 카카오_시도_이름을_AreaCode_약칭으로_바꾼다() {
        assertThat(OriginSearchService.shortSido("부산광역시")).isEqualTo("부산");
        assertThat(OriginSearchService.shortSido("경상북도")).isEqualTo("경북");
        assertThat(OriginSearchService.shortSido("충청남도")).isEqualTo("충남");
        assertThat(OriginSearchService.shortSido("전라남도")).isEqualTo("전남");
        assertThat(OriginSearchService.shortSido("강원특별자치도")).isEqualTo("강원");
        assertThat(OriginSearchService.shortSido("전북특별자치도")).isEqualTo("전북");
        assertThat(OriginSearchService.shortSido("경기도")).isEqualTo("경기");
        assertThat(idusw.sbb.checkin.domain.crowd.AreaCode.find("경북 경주시").signguCd()).isEqualTo("47130");
        assertThat(idusw.sbb.checkin.domain.crowd.AreaCode.find("부산 해운대구").signguCd()).isEqualTo("26350");
        assertThat(idusw.sbb.checkin.domain.crowd.AreaCode.find("경기 수원시 팔달구").signguCd()).isEqualTo("41115");
    }

    @Test
    void 키가_없으면_부르지_않고_UNAVAILABLE() {
        assertThat(svc("").search("부산역", null, null).status()).isEqualTo(Status.UNAVAILABLE);
        server.verify();   // 호출이 한 번도 없어야 한다
    }

    @Test
    void 카카오가_실패하면_결과없음이_아니라_UNAVAILABLE() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json")))
              .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(svc("k").search("부산역", null, null).status()).isEqualTo(Status.UNAVAILABLE);
    }

    @Test
    void 키워드가_비면_주소_검색으로_넘어가고_업종은_주소로_적는다() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json")))
              .andExpect(header("Authorization", "KakaoAK k"))
              .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/address.json")))
              .andRespond(withSuccess("""
                  {"documents":[{"address_name":"부산 해운대구 우동 1418","x":"129.1","y":"35.1",
                    "address":{"address_name":"부산 해운대구 우동 1418"},
                    "road_address":{"address_name":"부산 해운대구 해운대로394번길 25","building_name":""}}]}
                  """, MediaType.APPLICATION_JSON));

        OriginSearchService.Result r = svc("k").search("해운대구 우동 1418", null, null);
        assertThat(r.status()).isEqualTo(Status.OK);
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).category()).isEqualTo("주소");
        assertThat(r.items().get(0).name()).isEqualTo("부산 해운대구 해운대로394번길 25");
        server.verify();
    }

    @Test
    void 둘_다_비면_NONE_이고_업종묶음이_없으면_세부_업종을_쓴다() {
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json")))
              .andRespond(withSuccess("""
                  {"documents":[{"place_name":"합천일류돼지국밥","category_group_name":"",
                    "category_name":"음식점 > 한식 > 국밥","road_address_name":"부산 남구 ","address_name":"부산 남구",
                    "x":"129.0","y":"35.0","distance":""}]}
                  """, MediaType.APPLICATION_JSON));
        OriginSearchService.Result ok = svc("k").search("합천일류", null, null);
        assertThat(ok.items().get(0).category()).isEqualTo("국밥");
        assertThat(ok.items().get(0).distanceM()).isNull();

        server.reset();
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json")))
              .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/address.json")))
              .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));
        assertThat(svc("k").search("zzqx", null, null).status()).isEqualTo(Status.NONE);
    }
}
