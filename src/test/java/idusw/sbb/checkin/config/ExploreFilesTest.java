package idusw.sbb.checkin.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 전국 둘러보기 화면이 고르는 장면마다 모형 파일이 실제로 있어야 한다. 없으면 빈 판만 뜬다 */
class ExploreFilesTest {

    @Test
    void 목록의_장면마다_모형과_범위가_있다() throws Exception {
        JsonNode list;
        try (var in = new ClassPathResource("static/img/mass_index.json").getInputStream()) {
            list = new ObjectMapper().readTree(in);
        }
        assertThat(list.size()).isGreaterThan(250);
        Set<String> keys = new HashSet<>();
        for (JsonNode sc : list) {
            assertThat(keys.add(sc.path("key").asText())).as("겹친 장면 " + sc.path("key")).isTrue();
            assertThat(sc.path("bbox").has("s")).as(sc.path("label").asText()).isTrue();
            String svg = sc.path("svg").asText();
            assertThat(new ClassPathResource("static" + svg).exists()).as(svg).isTrue();
        }
    }

    @Test
    void 화면과_창이_첫_화면에_들어가_있다() throws Exception {
        String index;
        try (var in = new ClassPathResource("templates/index.html").getInputStream()) {
            index = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(index).contains("~{page_explore :: explore}", "/js/explore.js",
                "~{page_phone :: phone}", "/js/phone-qr.js");
    }
}
