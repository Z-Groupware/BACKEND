package com.module06.backend.notice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.notice.application.query.GetNoticeDetailQuery;
import com.module06.backend.notice.application.query.GetNoticeListQuery;
import com.module06.backend.notice.application.result.NoticeListResult;
import com.module06.backend.notice.domain.repository.NoticeQueryRepository;

/* NOTI-01 서비스의 회사 조건 전달과 빈 목록 및 입력 검증을 확인한다. */
@DisplayName("NOTI-01 공지 목록 조회 서비스")
class NoticeQueryServiceTest {

    /* 저장소 최신순 결과가 목록 애플리케이션 결과로 변환되는지 검증한다. */
    @Test
    @DisplayName("인증 회사의 활성 공지 목록을 저장소 순서대로 반환한다")
    void returnsCompanyNoticeListInRepositoryOrder() {
        /* 회사 식별자를 기록하고 최신순 공지 두 건을 반환하는 저장소 대역을 만든다. */
        Long[] capturedCompanyId = new Long[1];
        NoticeQueryRepository repository = repositoryForList(companyId -> {
            capturedCompanyId[0] = companyId;
            return List.of(
                    snapshot(2L, "두 번째 공지", 11, 0),
                    snapshot(1L, "첫 번째 공지", 10, 0)
            );
        });
        NoticeQueryService service = new NoticeQueryService(repository);

        /* 인증 회사 10의 공지 목록을 조회한다. */
        NoticeListResult result = service.getNotices(new GetNoticeListQuery(10L));

        /* 요청 회사만 저장소에 전달되고 저장소의 최신순이 결과에서도 유지돼야 한다. */
        assertThat(capturedCompanyId[0]).isEqualTo(10L);
        assertThat(result.notices())
                .extracting(NoticeListResult.NoticeItem::noticeId)
                .containsExactly(2L, 1L);
        assertThat(result.notices())
                .extracting(NoticeListResult.NoticeItem::title)
                .containsExactly("두 번째 공지", "첫 번째 공지");
    }

    /* 공지가 없는 회사도 예외가 아니라 빈 목록 결과를 받는지 검증한다. */
    @Test
    @DisplayName("활성 공지가 없으면 빈 목록을 반환한다")
    void returnsEmptyListWhenCompanyHasNoNotice() {
        /* 어떤 회사에서도 공지가 없는 저장소 대역으로 서비스를 구성한다. */
        NoticeQueryService service = new NoticeQueryService(repositoryForList(companyId -> List.of()));

        /* 공지가 없는 회사 10의 목록을 조회한다. */
        NoticeListResult result = service.getNotices(new GetNoticeListQuery(10L));

        /* NOTI-01은 404 대신 빈 notices 배열로 표현할 수 있는 결과를 반환해야 한다. */
        assertThat(result.notices()).isEmpty();
    }

    /* 인증 회사 식별자가 잘못된 내부 호출을 저장소 접근 전에 차단하는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 회사 식별자는 Z-001로 거절한다")
    void rejectsInvalidCompanyId() {
        /* 저장소가 호출되면 실패하도록 만들어 선행 검증을 확인한다. */
        NoticeQueryService service = new NoticeQueryService(repositoryForList(companyId -> {
            throw new AssertionError("잘못된 회사 식별자로 저장소를 호출하면 안 됩니다.");
        }));

        /* null Query와 양수가 아닌 회사 식별자는 모두 공통 입력 오류여야 한다. */
        assertInvalidInput(() -> service.getNotices(null));
        assertInvalidInput(() -> service.getNotices(new GetNoticeListQuery(null)));
        assertInvalidInput(() -> service.getNotices(new GetNoticeListQuery(0L)));
    }

    /* 같은 회사의 활성 공지 상세가 그대로 반환되는지 검증한다. */
    @Test
    @DisplayName("같은 회사의 활성 공지 상세를 반환한다")
    void returnsActiveNoticeDetailInsideCompanyScope() {
        /* 목록은 사용하지 않고 요청 회사와 공지가 일치할 때 상세 한 건을 반환하는 저장소를 만든다. */
        NoticeQueryRepository repository = new NoticeQueryRepository() {
            /* NOTI-02 테스트에서는 목록 조회를 사용하지 않는다. */
            @Override
            public List<NoticeListSnapshot> findActiveNoticesByCompanyId(Long companyId) {
                /* 상세 조회 외 호출은 테스트 실패로 처리한다. */
                throw new AssertionError("상세 조회에서 목록 저장소를 호출하면 안 됩니다.");
            }

            /* 회사 10의 공지 1에 대해서만 수정 전 공지 상세를 반환한다. */
            @Override
            public Optional<NoticeDetailSnapshot> findActiveNotice(Long companyId, Long noticeId) {
                /* 서비스가 회사와 공지 식별자를 모두 정확히 전달해야 결과가 존재한다. */
                if (companyId.equals(10L) && noticeId.equals(1L)) {
                    return Optional.of(detailSnapshot(null));
                }
                return Optional.empty();
            }
        };
        NoticeQueryService service = new NoticeQueryService(repository);

        /* 회사 10에서 공지 1의 상세를 조회한다. */
        var result = service.getNotice(new GetNoticeDetailQuery(10L, 1L));

        /* 제목·본문·생성 시각이 반환되고 수정 전 updatedAt은 null이어야 한다. */
        assertThat(result.noticeId()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("회의실 예약과 참석 안내");
        assertThat(result.content()).isEqualTo("회의는 회의실 예약 화면에서만 개설할 수 있습니다.");
        assertThat(result.updatedAt()).isNull();
    }

    /* 미존재·삭제·타 회사 공지가 모두 NT-001로 숨겨지는지 검증한다. */
    @Test
    @DisplayName("조회할 수 없는 공지는 NT-001로 처리한다")
    void hidesMissingDeletedOrOtherCompanyNotice() {
        /* 상세 조회에 항상 빈 결과를 반환하는 저장소로 세 경우의 공통 처리를 표현한다. */
        NoticeQueryRepository repository = new NoticeQueryRepository() {
            /* NOTI-02 테스트에서는 목록 조회를 사용하지 않는다. */
            @Override
            public List<NoticeListSnapshot> findActiveNoticesByCompanyId(Long companyId) {
                /* 상세 조회 외 호출은 테스트 실패로 처리한다. */
                throw new AssertionError("상세 조회에서 목록 저장소를 호출하면 안 됩니다.");
            }

            /* 미존재·삭제·타 회사 모두 저장소 경계에서는 빈 결과다. */
            @Override
            public Optional<NoticeDetailSnapshot> findActiveNotice(Long companyId, Long noticeId) {
                /* 리소스 존재 여부를 구분하지 않는 계약을 그대로 반환한다. */
                return Optional.empty();
            }
        };
        NoticeQueryService service = new NoticeQueryService(repository);

        /* 빈 저장소 결과는 공지 존재 여부를 노출하지 않는 NT-001이어야 한다. */
        assertThatThrownBy(() -> service.getNotice(
                new GetNoticeDetailQuery(10L, 99L)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("NT-001");
    }

    /* 잘못된 상세 조회 식별자가 저장소 호출 전에 거절되는지 검증한다. */
    @Test
    @DisplayName("유효하지 않은 공지 상세 Query는 Z-001로 거절한다")
    void rejectsInvalidNoticeDetailQuery() {
        /* 어떤 저장소 메서드가 호출돼도 실패하도록 만들어 서비스 선행 검증을 확인한다. */
        NoticeQueryRepository repository = new NoticeQueryRepository() {
            /* 잘못된 상세 Query에서는 목록 조회도 호출되지 않아야 한다. */
            @Override
            public List<NoticeListSnapshot> findActiveNoticesByCompanyId(Long companyId) {
                /* 저장소 접근은 선행 검증 누락이므로 테스트를 실패시킨다. */
                throw new AssertionError("잘못된 상세 Query로 저장소를 호출하면 안 됩니다.");
            }

            /* 잘못된 상세 Query에서는 상세 조회도 호출되지 않아야 한다. */
            @Override
            public Optional<NoticeDetailSnapshot> findActiveNotice(Long companyId, Long noticeId) {
                /* 저장소 접근은 선행 검증 누락이므로 테스트를 실패시킨다. */
                throw new AssertionError("잘못된 상세 Query로 저장소를 호출하면 안 됩니다.");
            }
        };
        NoticeQueryService service = new NoticeQueryService(repository);

        /* null Query와 양수가 아닌 회사·공지 식별자는 모두 공통 입력 오류여야 한다. */
        assertInvalidInput(() -> service.getNotice(null));
        assertInvalidInput(() -> service.getNotice(new GetNoticeDetailQuery(null, 1L)));
        assertInvalidInput(() -> service.getNotice(new GetNoticeDetailQuery(10L, null)));
        assertInvalidInput(() -> service.getNotice(new GetNoticeDetailQuery(10L, 0L)));
    }

    /* 테스트가 사용할 공지 목록 저장소 스냅샷을 만든다. */
    private NoticeQueryRepository.NoticeListSnapshot snapshot(Long id, String title, int hour, int minute) {
        /* 같은 날짜에서 시각만 다른 공지 목록 행을 반환한다. */
        return new NoticeQueryRepository.NoticeListSnapshot(
                id,
                title,
                LocalDateTime.of(2026, 8, 9, hour, minute)
        );
    }

    /* 수정 전 공지 상세 저장소 스냅샷을 만든다. */
    private NoticeQueryRepository.NoticeDetailSnapshot detailSnapshot(LocalDateTime updatedAt) {
        /* 명세 예시의 제목·본문·생성 시각과 선택 수정 시각을 담아 반환한다. */
        return new NoticeQueryRepository.NoticeDetailSnapshot(
                1L,
                "회의실 예약과 참석 안내",
                "회의는 회의실 예약 화면에서만 개설할 수 있습니다.",
                LocalDateTime.of(2026, 8, 3, 10, 12),
                updatedAt
        );
    }

    /* 목록 조회 테스트가 사용할 함수형 저장소 대역을 완성한다. */
    private NoticeQueryRepository repositoryForList(
            Function<Long, List<NoticeQueryRepository.NoticeListSnapshot>> finder
    ) {
        /* 목록 함수만 위임하고 상세 조회는 이 테스트에서 사용하지 않는 빈 결과로 둔다. */
        return new NoticeQueryRepository() {
            /* 테스트가 전달한 목록 조회 함수를 실행한다. */
            @Override
            public List<NoticeListSnapshot> findActiveNoticesByCompanyId(Long companyId) {
                /* 회사 식별자 기록과 결과 준비 책임을 호출자 함수에 맡긴다. */
                return finder.apply(companyId);
            }

            /* 목록 테스트에서는 상세 조회가 호출되지 않아야 한다. */
            @Override
            public Optional<NoticeDetailSnapshot> findActiveNotice(Long companyId, Long noticeId) {
                /* 사용하지 않는 계약은 빈 결과로 안전하게 닫는다. */
                return Optional.empty();
            }
        };
    }

    /* 실행 결과가 Z-001 BusinessException인지 공통으로 검증한다. */
    private void assertInvalidInput(Runnable executable) {
        /* 잘못된 인증 Query는 저장소 예외가 아닌 공통 입력 오류로 변환돼야 한다. */
        assertThatThrownBy(executable::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("Z-001");
    }
}
