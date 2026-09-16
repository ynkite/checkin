package idusw.sbb.checkin.domain.budget.dto;

import java.util.List;

/**
 * 예산 산출 결과.
 *
 * 화면이 「37만원」만 보여 주면 사용자는 그 값을 믿을지 말지 알 수 없다.
 * 항목마다 근거(basis)와 어림값인지(estimated)를 같이 보낸다. accuracy 는
 * 몇 항목이 실제 자료에서 왔는지를 말한다.
 */
public record BudgetEstimate(
        long total,
        long perPerson,
        int  people,
        int  nights,
        int  days,
        List<Item> items,
        Season season,
        List<Festival> festivals,
        Accuracy accuracy,
        List<String> notes
) {
    /**
     * @param category LODGING | FOOD | TRANSPORT | TOUR
     * @param basis    이 금액이 어디서 왔는지. 사람이 읽는 한 줄
     */
    public record Item(
            String  category,
            String  label,
            long    amount,
            String  basis,
            boolean estimated,
            String  seasonApplied      // 성수기 배수를 걸었으면 그 이름. 안 걸었으면 null
    ) {}

    public record Season(
            String key,
            String label,
            double carMultiplier,
            double foodMultiplier,
            boolean weekendCheckIn,
            List<String> peakDays      // YYYY-MM-DD
    ) {}

    /**
     * @param level  HIGH | MEDIUM | LOW
     * @param real   실제 자료에서 온 항목 수
     * @param guessed 가정으로 메운 항목 수
     */
    public record Accuracy(String level, String label, int real, int guessed, String why) {}
}
