package idusw.sbb.checkin.domain.route.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AiRouteService} 의 저장 경로가 쓰기 트랜잭션을 들고 있는지 지킨다.
 *
 * <p>클래스에 {@code @Transactional(readOnly = true)} 가 걸려 있어서, 저장하는 메서드가 자기
 * 애너테이션을 잃으면 <b>조용히 읽기 전용으로 떨어진다.</b> readOnly 트랜잭션은 FlushMode 가
 * MANUAL 이라 insert·update 가 flush 되지 않고 사라진다 — 예외도 로그도 없다.
 *
 * <p>실제로 한 번 일어났다. 집중률 기능이 들어오면서 {@code @Transactional} 과
 * {@code saveAiRouteToDb} 선언 사이에 private 메서드가 끼어들었고, 애너테이션이 그 private
 * 메서드로 옮겨 붙어(= Spring 이 무시) 동선 저장과 예상비용이 통째로 버려졌다. git 은 충돌 없이
 * 머지했고 컴파일도 통과했다. 같은 사고가 다시 나면 여기서 빌드가 깨진다.
 */
class AiRouteServiceTransactionGuardTest {

    /** 컨트롤러가 직접 부르거나(프록시 통과) 내부 저장 체인을 여는 진입점들. */
    private static final List<String> WRITE_ENTRY_POINTS = List.of(
            "saveAiRouteToDb",
            "finalizeRoute",
            "replaceAiRoutePlaces",
            "replaceDayWithIndoor");

    @Test
    void 저장_경로는_쓰기_트랜잭션을_들고_있어야_한다() {
        for (String name : WRITE_ENTRY_POINTS) {
            Method method = findMethod(name);
            Transactional annotation = method.getAnnotation(Transactional.class);

            assertThat(annotation)
                    .as("%s 에 @Transactional 이 없다 — 클래스 레벨 readOnly 로 떨어져 저장이 버려진다."
                            + " 애너테이션과 메서드 선언 사이에 다른 선언이 끼어들지 않았는지 확인할 것", name)
                    .isNotNull();
            assertThat(annotation.readOnly())
                    .as("%s 가 readOnly 트랜잭션이다 — insert·update 가 flush 되지 않는다", name)
                    .isFalse();
        }
    }

    /**
     * 클래스 기본값이 readOnly 라는 사실이 위 검사의 전제다. 이게 바뀌면 위 테스트가 지키는
     * 의미도 달라지므로 같이 고정한다.
     */
    @Test
    void 클래스_기본값은_읽기_전용이다() {
        Transactional classLevel = AiRouteService.class.getAnnotation(Transactional.class);

        assertThat(classLevel).isNotNull();
        assertThat(classLevel.readOnly()).isTrue();
    }

    private static Method findMethod(String name) {
        List<Method> found = Arrays.stream(AiRouteService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .toList();

        assertThat(found).as("%s 를 찾지 못했다 — 이름이 바뀌었으면 이 목록도 같이 고칠 것", name).hasSize(1);
        return found.get(0);
    }
}
