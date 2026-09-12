package idusw.sbb.checkin.domain.route.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {

    private static final GeoPoint LOCATION = new GeoPoint(35.1595, 129.0756);

    private Candidate candidate(String id) {
        return new Candidate(id, id, LOCATION, CandidateCategory.TOUR, 60, null, null, null);
    }

    @Test
    void 후보가_5개를_넘으면_예외() {
        List<Candidate> sixCandidates = List.of(
                candidate("1"), candidate("2"), candidate("3"),
                candidate("4"), candidate("5"), candidate("6"));

        assertThatThrownBy(() -> new TimeSlot(SlotType.AFTERNOON_ACTIVITY, sixCandidates))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 후보가_5개_이하면_생성된다() {
        List<Candidate> five = List.of(
                candidate("1"), candidate("2"), candidate("3"), candidate("4"), candidate("5"));

        TimeSlot slot = new TimeSlot(SlotType.AFTERNOON_ACTIVITY, five);
        assertThat(slot.size()).isEqualTo(5);
    }

    @Test
    void 후보_목록은_불변이다() {
        List<Candidate> mutable = new ArrayList<>(List.of(candidate("1")));
        TimeSlot slot = new TimeSlot(SlotType.LUNCH, mutable);

        mutable.add(candidate("2"));
        assertThat(slot.size()).isEqualTo(1);

        assertThatThrownBy(() -> slot.candidates().add(candidate("3")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 점심과_저녁만_식사_슬롯이다() {
        TimeSlot lunch = new TimeSlot(SlotType.LUNCH, List.of());
        TimeSlot dinner = new TimeSlot(SlotType.DINNER, List.of());
        TimeSlot activity = new TimeSlot(SlotType.MORNING_ACTIVITY, List.of());

        assertThat(lunch.isMealSlot()).isTrue();
        assertThat(dinner.isMealSlot()).isTrue();
        assertThat(activity.isMealSlot()).isFalse();
    }
}
