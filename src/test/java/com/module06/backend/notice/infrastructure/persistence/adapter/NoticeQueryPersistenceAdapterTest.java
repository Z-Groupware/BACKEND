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
import com.module06.backend.notice.domain.repository.NoticeCommandRepository;
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

    /* 애플리케이션 명령 서비스가 사용하는 공지 저장 도메인 계약이다. */
    @Autowired
    private NoticeCommandRepository noticeCommandRepository;

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

    /* 공지 상세 조회가 회사·활성 조건을 모두 적용하는지 검증한다. */
    @Test
    @DisplayName("같은 회사의 활성 공지만 상세 조회한다")
    void findsNoticeDetailOnlyInsideCompanyScope() {
        /* 같은 회사의 활성·삭제 공지와 다른 회사의 활성 공지를 각각 저장한다. */
        NoticeJpaEntity active = springDataNoticeRepository.saveAndFlush(
                notice(10L, "활성 공지", null)
        );
        NoticeJpaEntity deleted = springDataNoticeRepository.saveAndFlush(
                notice(10L, "삭제 공지", LocalDateTime.of(2026, 8, 9, 12, 0))
        );
        NoticeJpaEntity otherCompany = springDataNoticeRepository.saveAndFlush(
                notice(20L, "다른 회사 공지", null)
        );

        /* 같은 회사의 활성 공지는 제목·본문을 포함한 상세 스냅샷으로 조회돼야 한다. */
        assertThat(noticeQueryRepository.findActiveNotice(10L, active.getId()))
                .isPresent()
                .get()
                .satisfies(notice -> {
                    /* 상세 응답 원본의 식별자·제목·본문이 저장값과 일치해야 한다. */
                    assertThat(notice.noticeId()).isEqualTo(active.getId());
                    assertThat(notice.title()).isEqualTo("활성 공지");
                    assertThat(notice.content()).isEqualTo("활성 공지 본문");
                    assertThat(notice.updatedAt()).isNull();
                });

        /* 삭제 공지와 다른 회사 공지는 존재 여부를 구분하지 않고 빈 결과로 숨겨야 한다. */
        assertThat(noticeQueryRepository.findActiveNotice(10L, deleted.getId())).isEmpty();
        assertThat(noticeQueryRepository.findActiveNotice(10L, otherCompany.getId())).isEmpty();
        assertThat(noticeQueryRepository.findActiveNotice(10L, 999_999L)).isEmpty();
    }

    /* 신규 공지 저장 시 인증 회사·작성자와 생성 식별자·시각이 반영되는지 검증한다. */
    @Test
    @DisplayName("신규 공지를 저장하고 데이터베이스 생성 값을 반환한다")
    void savesNewNoticeWithGeneratedValues() {
        /* 인증 회사 10의 OWNER 3이 작성할 신규 공지 도메인 모델을 만든다. */
        Notice notice = Notice.create(
                10L,
                3L,
                "회의실 예약과 참석 안내",
                "회의는 회의실 예약 화면에서만 개설할 수 있습니다."
        );

        /* NOTI-03 명령 저장소로 신규 공지를 저장한다. */
        Notice savedNotice = noticeCommandRepository.save(notice);

        /* 데이터베이스 식별자·생성 시각이 생기고 인증 원본과 수정 전 상태가 유지돼야 한다. */
        assertThat(savedNotice.getId()).isPositive();
        assertThat(savedNotice.getCompanyId()).isEqualTo(10L);
        assertThat(savedNotice.getCreatedBy()).isEqualTo(3L);
        assertThat(savedNotice.getCreatedAt()).isNotNull();
        assertThat(savedNotice.getUpdatedAt()).isNull();
        assertThat(savedNotice.getDeletedAt()).isNull();
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
