package idusw.sbb.triplinker.domain.post.repository;

import idusw.sbb.triplinker.domain.post.entity.PostViewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostViewLogRepository extends JpaRepository<PostViewLog, Long> {

    // 관리자 통계 - 기간 내 발생한 게시글 조회 건수 (= visitCount)
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 관리자 통계 - 일자별 조회 발생 건수 (추이 차트용)
    @Query("SELECT FUNCTION('DATE', v.createdAt), COUNT(v) FROM PostViewLog v " +
            "WHERE v.createdAt BETWEEN :start AND :end GROUP BY FUNCTION('DATE', v.createdAt)")
    List<Object[]> countDailyViews(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}