package com.module06.backend.notice.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.notice.domain.model.Notice;
import com.module06.backend.notice.domain.repository.NoticeQueryRepository;
import com.module06.backend.notice.infrastructure.persistence.entity.NoticeJpaEntity;
import com.module06.backend.notice.infrastructure.persistence.repository.SpringDataNoticeRepository;

/* NOTI-01 영속성 어댑터의 회사 격리·소프트 삭제·최신순 조회를 실제 JPA로 검증한다. */
@SpringBootTest
@Transactional
@DisplayName("NOTI-01 공지 목록 조회 영속성 어댑터")
class NoticeQueryPersistenceAdapterTest {

    /* 애플리케이션이 사용하는 공지 목록 도메인 저장소 계약이다. */
    @Autowired
    private NoticeQueryRepository noticeQueryRepository;

    /* 테스트 공지 행을 저장하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataNoticeRepository springDataNoticeRepository;

    /* 동일 생성 시각의 보조 정렬을 검증하기 위해 테스트 행의 시각을 직접 고정하는 영속성 컨텍스트다. */
    @Autowired
    private EntityManager entityManager;

    /* 각 테스트가 독립된 공지 데이터로 실행되도록 notice 테이블을 초기화한다. */
    @BeforeEach
    void clearNoticeData() {
        /* 공지는 다른 테스트 데이터의 자식이 아니므로 기술 저장소에서 바로 삭제한다. */
        springDataNoticeRepository.deleteAll();
    }

    /* 회사·활성 조건과 안정적인 최신순 정렬이 함께 적용되는지 검증한다. */
    @Test
    @DisplayName("같은 회사의 활성 공지만 최신순으로 조회한다")
    void findsOnlyActiveNoticesInsideCompanyScopeInLatestOrder() {
        /* 회사 10의 오래된 활성 공지를 먼저 저장한다. */
        NoticeJpaEntity older = springDataNoticeRepository.saveAndFlush(
                notice(10L, "첫 번째 공지", null)
        );

        /* 회사 10의 삭제 공지와 회사 20의 공지를 섞어 테넌트·삭제 조건을 검증한다. */
        springDataNoticeRepository.saveAndFlush(
                notice(10L, "삭제된 공지", LocalDateTime.of(2026, 8, 9, 12, 0))
        );
        springDataNoticeRepository.saveAndFlush(
                notice(20L, "다른 회사 공지", null)
        );

        /* 회사 10에서 더 큰 식별자를 가질 두 번째 활성 공지를 마지막으로 저장한다. */
        NoticeJpaEntity newer = springDataNoticeRepository.saveAndFlush(
                notice(10L, "두 번째 공지", null)
        );

        /* 두 활성 공지의 생성 시각을 같게 만들어 id DESC가 없으면 순서를 보장할 수 없게 한다. */
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 8, 9, 10, 0);
        forceCreatedAt(older.getId(), sameCreatedAt);
        forceCreatedAt(newer.getId(), sameCreatedAt);

        /* 관리 중인 엔티티를 비워 목록 조회가 고정된 데이터베이스 값을 다시 읽게 한다. */
        entityManager.flush();
        entityManager.clear();

        /* 인증 회사 10의 활성 공지 목록을 조회한다. */
        var result = noticeQueryRepository.findActiveNoticesByCompanyId(10L);

        /* 두 결과의 생성 시각이 실제로 동일해야 id DESC 보조 정렬 검증이 유효하다. */
        assertThat(result)
                .extracting(NoticeQueryRepository.NoticeListSnapshot::createdAt)
                .containsOnly(sameCreatedAt);

        /* 삭제·타 회사 행은 제외되고 동일 시각에는 큰 식별자가 먼저 와야 한다. */
        assertThat(result)
                .extracting(NoticeQueryRepository.NoticeListSnapshot::noticeId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(result)
                .extracting(NoticeQueryRepository.NoticeListSnapshot::title)
                .containsExactly("두 번째 공지", "첫 번째 공지");
    }

    /* 조회 회사에 활성 공지가 없을 때 빈 결과를 반환하는지 검증한다. */
    @Test
    @DisplayName("활성 공지가 없는 회사는 빈 목록을 반환한다")
    void returnsEmptyListWhenNoActiveNoticeExists() {
        /* 다른 회사 공지만 저장해 요청 회사에는 조회 대상이 없도록 한다. */
        springDataNoticeRepository.saveAndFlush(notice(20L, "다른 회사 공지", null));

        /* 회사 10 범위 조회는 예외 없이 빈 목록이어야 한다. */
        assertThat(noticeQueryRepository.findActiveNoticesByCompanyId(10L)).isEmpty();
    }

    /* 영속성 테스트에 사용할 신규 또는 삭제 공지 엔티티를 만든다. */
    private NoticeJpaEntity notice(Long companyId, String title, LocalDateTime deletedAt) {
        /* 생성 시각은 Hibernate가 채우고 삭제 여부만 테스트 조건에 맞춰 복원한다. */
        Notice notice = Notice.reconstitute(
                null,
                companyId,
                title,
                title + " 본문",
                3L,
                deletedAt,
                null,
                null
        );

        /* 도메인 공지를 저장 가능한 JPA 엔티티로 변환해 반환한다. */
        return NoticeJpaEntity.from(notice);
    }

    /* 생성 시각 보조 정렬 테스트를 위해 특정 공지 행의 created_at을 동일한 값으로 고정한다. */
    private void forceCreatedAt(Long noticeId, LocalDateTime createdAt) {
        /* 테스트 전용 JPQL 갱신으로 운영 엔티티에 시각 변경 메서드를 노출하지 않는다. */
        entityManager.createQuery("""
                        UPDATE NoticeJpaEntity notice
                        SET notice.createdAt = :createdAt
                        WHERE notice.id = :noticeId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("noticeId", noticeId)
                .executeUpdate();
    }
}
