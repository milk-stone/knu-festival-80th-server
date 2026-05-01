package kr.ac.knu.festival.domain.booth.repository;

import jakarta.persistence.LockModeType;
import kr.ac.knu.festival.domain.booth.entity.Booth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    List<Booth> findAllByOrderByLikeCountDescIdAsc();

    /**
     * 트랜잭션 내에서 부스 행을 SELECT ... FOR UPDATE 로 잡는다.
     * 대기열 채번/순서 변경/부스 삭제 등 부스 단위 직렬화가 필요한 곳에서 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booth b WHERE b.id = :id")
    Optional<Booth> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Booth b SET b.likeCount = b.likeCount + 1 WHERE b.id = :id")
    int incrementLike(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Booth b SET b.likeCount = b.likeCount - 1 WHERE b.id = :id AND b.likeCount > 0")
    int decrementLike(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Booth b SET b.likeCount = :likeCount WHERE b.id = :id")
    int updateLikeCount(@Param("id") Long id, @Param("likeCount") int likeCount);
}
