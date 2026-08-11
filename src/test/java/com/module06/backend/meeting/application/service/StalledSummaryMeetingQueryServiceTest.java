package com.module06.backend.meeting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.out.SummaryStatusQueryPort;
import com.module06.backend.meeting.application.query.GetStalledSummaryMeetingsQuery;
import com.module06.backend.meeting.application.result.StalledSummaryMeetingListResult;
import com.module06.backend.meeting.domain.repository.StalledSummaryMeetingRepository;

/* MEET-15 서비스의 D 후보·A 판정 교집합, 필터·페이징과 단축 경로를 검증한다. */
@DisplayName("MEET-15 요약 중단 회의 조회 서비스")
class StalledSummaryMeetingQueryServiceTest {

    /* A가 반환한 중단·실패 회의만 최근 순으로 조립되는지 검증한다. */
    @Test
    @DisplayName("A가 반환한 회의만 남기고 중단 여부를 조립한다")
    void keepsOnlyMeetingsReturnedByAnalysisDomain() {
        /* host의 종료 회의 세 건과 그중 두 건에 대한 A 판정 결과를 준비한다. */
        RecordingRepository repository = new RecordingRepository(List.of(
                candidate(30L, 12L, "9월 스프린트 리뷰", LocalDateTime.of(2026, 8, 9, 14, 0)),
                candidate(29L, 12L, "현업툴 리뉴얼 킥오프", LocalDateTime.of(2026, 8, 8, 14, 0)),
                candidate(28L, 15L, "정상 요약 회의", LocalDateTime.of(2026, 8, 7, 14, 0))
        ));
        RecordingSummaryStatusQueryPort summaryPort = new RecordingSummaryStatusQueryPort(List.of(
                new SummaryStatusQueryPort.StalledSummaryMeeting(30L, true),
                new SummaryStatusQueryPort.StalledSummaryMeeting(29L, false)
        ));
        StalledSummaryMeetingQueryService service = new StalledSummaryMeetingQueryService(
                repository,
                summaryPort
        );

        /* 로그인 사용자 3번의 첫 페이지 요약 문제 회의를 조회한다. */
        StalledSummaryMeetingListResult result = service.getStalledSummaryMeetings(query(null, null, null, 0, 20));

        /* A가 반환하지 않은 정상 요약 회의는 목록에서 제외돼야 한다. */
        assertThat(result.meetings())
                .extracting(StalledSummaryMeetingListResult.MeetingItem::meetingId)
                .containsExactly(30L, 29L);

        /* true 중단과 false 실패 판정이 A의 결과 그대로 응답에 실려야 한다. */
        assertThat(result.meetings())
                .extracting(StalledSummaryMeetingListResult.MeetingItem::stalled)
                .containsExactly(true, false);

        /* 저장소와 A Port에는 인증 principal의 회사·구성원 및 후보 전체가 전달돼야 한다. */
        assertThat(repository.capturedCompanyId).isEqualTo(10L);
        assertThat(repository.capturedHostMemberId).isEqualTo(3L);
        assertThat(summaryPort.capturedCompanyId).isEqualTo(10L);
        assertThat(summaryPort.capturedMeetingIds).containsExactly(30L, 29L, 28L);
    }

    /* 프로젝트와 기간 필터가 A 호출 전에 후보를 좁히는지 검증한다. */
    @Test
    @DisplayName("프로젝트와 기간 필터를 통과한 후보만 A에 전달한다")
    void filtersCandidatesBeforeCallingAnalysisDomain() {
        /* 서로 다른 프로젝트와 날짜의 종료 회의를 준비한다. */
        RecordingSummaryStatusQueryPort summaryPort = new RecordingSummaryStatusQueryPort(List.of(
                new SummaryStatusQueryPort.StalledSummaryMeeting(30L, true)
        ));
        StalledSummaryMeetingQueryService service = new StalledSummaryMeetingQueryService(
                new RecordingRepository(List.of(
                        candidate(30L, 12L, "필터 통과", LocalDateTime.of(2026, 8, 9, 14, 0)),
                        candidate(29L, 15L, "다른 프로젝트", LocalDateTime.of(2026, 8, 9, 14, 0)),
                        candidate(28L, 12L, "기간 이전", LocalDateTime.of(2026, 7, 31, 14, 0))
                )),
                summaryPort
        );

        /* 프로젝트 12번의 8월 회의만 조회한다. */
        service.getStalledSummaryMeetings(query(
                12L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                0,
                20
        ));

        /* A에는 필터를 통과한 30번 회의만 한 번의 배치로 전달돼야 한다. */
        assertThat(summaryPort.invocationCount).isEqualTo(1);
        assertThat(summaryPort.capturedMeetingIds).containsExactly(30L);
    }

    /* A 교집합 결과를 기준으로 페이지 내용과 전체 건수를 계산하는지 검증한다. */
    @Test
    @DisplayName("A 교집합 결과를 기준으로 페이지를 계산한다")
    void paginatesMatchedMeetings() {
        /* 문제 회의 다섯 건을 최근 순 후보와 A 결과로 동일하게 준비한다. */
        List<StalledSummaryMeetingRepository.StalledSummaryMeetingCandidate> candidates = List.of(
                candidate(35L, 12L, "회의 5", LocalDateTime.of(2026, 8, 10, 14, 0)),
                candidate(34L, 12L, "회의 4", LocalDateTime.of(2026, 8, 9, 14, 0)),
                candidate(33L, 12L, "회의 3", LocalDateTime.of(2026, 8, 8, 14, 0)),
                candidate(32L, 12L, "회의 2", LocalDateTime.of(2026, 8, 7, 14, 0)),
                candidate(31L, 12L, "회의 1", LocalDateTime.of(2026, 8, 6, 14, 0))
        );
        List<SummaryStatusQueryPort.StalledSummaryMeeting> summaries = candidates.stream()
                .map(candidate -> new SummaryStatusQueryPort.StalledSummaryMeeting(candidate.meetingId(), true))
                .toList();
        StalledSummaryMeetingQueryService service = new StalledSummaryMeetingQueryService(
                new RecordingRepository(candidates),
                new RecordingSummaryStatusQueryPort(summaries)
        );

        /* 페이지 크기 2의 두 번째 페이지를 조회한다. */
        StalledSummaryMeetingListResult result = service.getStalledSummaryMeetings(
                query(null, null, null, 1, 2)
        );

        /* 최근 순 세 번째·네 번째 항목과 전체 5건·3페이지 메타가 반환돼야 한다. */
        assertThat(result.meetings())
                .extracting(StalledSummaryMeetingListResult.MeetingItem::meetingId)
                .containsExactly(33L, 32L);
        assertThat(result.page().totalElements()).isEqualTo(5L);
        assertThat(result.page().totalPages()).isEqualTo(3);
    }

    /* 후보가 없으면 A Port를 호출하지 않는지 검증한다. */
    @Test
    @DisplayName("후보 회의가 없으면 A Port를 호출하지 않는다")
    void skipsAnalysisPortWhenNoCandidate() {
        /* 후보 없는 저장소와 호출 시 실패하는 A Port로 서비스를 구성한다. */
        StalledSummaryMeetingQueryService service = new StalledSummaryMeetingQueryService(
                new RecordingRepository(List.of()),
                (companyId, meetingIds) -> fail("후보가 없으면 A 요약 상태 Port를 호출하면 안 된다.")
        );

        /* 후보가 없는 사용자의 첫 페이지를 조회한다. */
        StalledSummaryMeetingListResult result = service.getStalledSummaryMeetings(
                query(null, null, null, 0, 20)
        );

        /* 외부 호출 없이 빈 목록과 0건 페이지 메타가 반환돼야 한다. */
        assertThat(result.meetings()).isEmpty();
        assertThat(result.page().totalElements()).isZero();
        assertThat(result.page().totalPages()).isZero();
    }

    /* 잘못된 기간과 페이지 값이 저장소 접근 전에 거절되는지 검증한다. */
    @Test
    @DisplayName("잘못된 기간과 페이지 값은 Z-001로 거절한다")
    void rejectsInvalidFilters() {
        /* 저장소가 호출되면 실패하도록 빈 대역으로 서비스를 구성한다. */
        StalledSummaryMeetingQueryService service = new StalledSummaryMeetingQueryService(
                new RecordingRepository(List.of()),
                (companyId, meetingIds) -> List.of()
        );

        /* 시작일이 종료일보다 늦은 범위는 공통 입력값 오류로 거절돼야 한다. */
        assertThatThrownBy(() -> service.getStalledSummaryMeetings(query(
                null,
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 8, 1),
                0,
                20
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("입력값");

        /* 최대 크기를 넘는 페이지 요청도 같은 입력값 오류로 거절돼야 한다. */
        assertThatThrownBy(() -> service.getStalledSummaryMeetings(query(null, null, null, 0, 101)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("입력값");
    }

    /* 공통 인증 값과 선택 필터로 서비스 Query를 만든다. */
    private GetStalledSummaryMeetingsQuery query(
            Long projectId,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size
    ) {
        /* 테스트 로그인 사용자를 회사 10번의 구성원 3번으로 고정한다. */
        return new GetStalledSummaryMeetingsQuery(10L, 3L, projectId, from, to, page, size);
    }

    /* 저장소 후보 회의 읽기 모델을 만든다. */
    private StalledSummaryMeetingRepository.StalledSummaryMeetingCandidate candidate(
            Long meetingId,
            Long projectId,
            String title,
            LocalDateTime startAt
    ) {
        /* MEET-10과 MEET-15가 공유하는 D 소유 종료 회의 후보를 구성한다. */
        return new StalledSummaryMeetingRepository.StalledSummaryMeetingCandidate(
                meetingId, projectId, title, startAt
        );
    }

    /* 저장소 호출 조건을 기록하고 준비한 후보를 반환하는 테스트 대역이다. */
    private static final class RecordingRepository implements StalledSummaryMeetingRepository {

        /* 조회 시 반환할 최근 순 종료 회의 후보다. */
        private final List<StalledSummaryMeetingCandidate> candidates;

        /* 서비스가 전달한 인증 회사 식별자를 기록한다. */
        private Long capturedCompanyId;

        /* 서비스가 전달한 host 구성원 식별자를 기록한다. */
        private Long capturedHostMemberId;

        /* 준비한 후보를 외부 변경이 불가능한 목록으로 보관한다. */
        private RecordingRepository(List<StalledSummaryMeetingCandidate> candidates) {
            /* 테스트 도중 후보 목록이 바뀌지 않도록 불변 복사한다. */
            this.candidates = List.copyOf(candidates);
        }

        /* 회사·host 조건을 기록하고 준비한 종료 회의를 반환한다. */
        @Override
        public List<StalledSummaryMeetingCandidate> findHostedDoneSummaryCandidates(
                Long companyId,
                Long hostMemberId
        ) {
            /* 저장소 경계로 전달된 인증 값을 이후 단언을 위해 보관한다. */
            this.capturedCompanyId = companyId;
            this.capturedHostMemberId = hostMemberId;
            return candidates;
        }
    }

    /* A Port 호출 횟수와 배치 인자를 기록하는 테스트 대역이다. */
    private static final class RecordingSummaryStatusQueryPort implements SummaryStatusQueryPort {

        /* A가 반환할 중단·실패 회의 판정이다. */
        private final List<StalledSummaryMeeting> summaries;

        /* 배치 호출이 한 번인지 검증할 호출 횟수다. */
        private int invocationCount;

        /* A 경계로 전달된 회사 식별자를 기록한다. */
        private Long capturedCompanyId;

        /* A 경계로 전달된 후보 회의 전체를 기록한다. */
        private List<Long> capturedMeetingIds = List.of();

        /* 준비한 A 판정을 외부 변경이 불가능한 목록으로 보관한다. */
        private RecordingSummaryStatusQueryPort(List<StalledSummaryMeeting> summaries) {
            /* 테스트 도중 반환 판정이 바뀌지 않도록 불변 복사한다. */
            this.summaries = List.copyOf(summaries);
        }

        /* 호출 인자를 기록하고 준비한 요약 문제 회의를 반환한다. */
        @Override
        public List<StalledSummaryMeeting> findStalledSummaries(Long companyId, List<Long> meetingIds) {
            /* 회의별 반복 호출 여부와 테넌트·배치 범위를 검증할 값을 기록한다. */
            invocationCount++;
            capturedCompanyId = companyId;
            capturedMeetingIds = List.copyOf(meetingIds);
            return summaries;
        }
    }
}
