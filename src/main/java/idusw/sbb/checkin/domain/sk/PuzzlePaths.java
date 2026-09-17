package idusw.sbb.checkin.domain.sk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 퍼즐 데이터셋 경로.
 *
 * 상품마다 버전이 달라서 신청한 뒤에 문서를 보고 맞춰야 한다.
 * 그때 자바를 다시 빌드하지 않게 설정으로 뺐다.
 * `{station}` `{poi}` `{area}` 자리는 PuzzleService 가 채운다.
 */
@Component
public class PuzzlePaths {

    @Value("${sk.puzzle.path.subway:/subway/congestion/stat/hourly/stations/{station}}")
    private String subway;

    @Value("${sk.puzzle.path.place:/place/congestion/stat/hourly/pois/{poi}}")
    private String place;

    @Value("${sk.puzzle.path.pop:/pop/congestion/stat/hourly/areas/{area}}")
    private String pop;

    @Value("${sk.puzzle.path.travel:/travel/visitor/stat/monthly/areas/{area}}")
    private String travel;

    @Value("${sk.puzzle.path.dining:/dining/sales/stat/monthly/areas/{area}}")
    private String dining;

    @Value("${sk.puzzle.path.residence:/residence/pop/stat/monthly/areas/{area}}")
    private String residence;

    @Value("${sk.puzzle.path.academy:/academy/sales/stat/monthly/areas/{area}}")
    private String academy;

    public String subway()    { return subway; }
    public String place()     { return place; }
    public String pop()       { return pop; }
    public String travel()    { return travel; }
    public String dining()    { return dining; }
    public String residence() { return residence; }
    public String academy()   { return academy; }
}
