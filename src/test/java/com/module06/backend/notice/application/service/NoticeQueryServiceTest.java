package com.module06.backend.notice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
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
        NoticeQueryRepository repository = companyId -> {
            capturedCompanyId[0] = companyId;
            return List.of(
                    snapshot(2L, "두 번째 공지", 11, 0),
                    snapshot(1L, "첫 번째 공지", 10, 0)
            );
        };
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
        NoticeQueryService service = new NoticeQueryService(companyId -> List.of());

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
        NoticeQueryService service = new NoticeQueryService(companyId -> {
            throw new AssertionError("잘못된 회사 식별자로 저장소를 호출하면 안 됩니다.");
        });

        /* null Query와 양수가 아닌 회사 식별자는 모두 공통 입력 오류여야 한다. */
        assertInvalidInput(() -> service.getNotices(null));
        assertInvalidInput(() -> service.getNotices(new GetNoticeListQuery(null)));
        assertInvalidInput(() -> service.getNotices(new GetNoticeListQuery(0L)));
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

    /* 실행 결과가 Z-001 BusinessException인지 공통으로 검증한다. */
    private void assertInvalidInput(Runnable executable) {
        /* 잘못된 인증 Query는 저장소 예외가 아닌 공통 입력 오류로 변환돼야 한다. */
        assertThatThrownBy(executable::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("Z-001");
    }
}
