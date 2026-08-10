package com.module06.backend.meeting.application.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/* 출처 회의 히스토리 결과가 상류 요약의 제공 상태와 불변성을 보존하는지 검증한다. */
@DisplayName("출처 회의 히스토리 결과")
class MeetingHistoryResultTest {

    /* A/C가 제공한 결정·액션 목록을 생성 이후 변경할 수 없는지 검증한다. */
    @Test
    @DisplayName("결정과 액션 요약 목록을 불변 스냅샷으로 보관한다")
    void copiesDecisionAndActionSummaries() {
        /* 생성 후 원본 목록 변경이 결과에 전파되는지 확인하기 위한 가변 목록을 준비한다. */
        List<MeetingHistoryResult.DecisionSummary> decisions = new ArrayList<>(List.of(
                new MeetingHistoryResult.DecisionSummary(1L, "출시일을 확정한다.", "QA 일정이 확보됐다.")
        ));
        List<MeetingHistoryResult.ActionSummary> actions = new ArrayList<>(List.of(
                new MeetingHistoryResult.ActionSummary(
                        2L,
                        "PERSONAL",
                        "배포 체크리스트 작성",
                        "TODO",
                        LocalDate.of(2026, 8, 14),
                        "모성진",
                        null
                )
        ));

        /* A/C 요약까지 제공된 출처 회의 결과를 생성한다. */
        MeetingHistoryResult result = history(decisions, actions);

        /* 원본 가변 목록을 비워도 결과가 생성 시점의 항목을 유지해야 한다. */
        decisions.clear();
        actions.clear();

        /* 결정과 액션이 각각 한 건씩 보존되고 표시 본문도 손실되지 않아야 한다. */
        assertThat(result.decisions()).singleElement()
                .extracting(MeetingHistoryResult.DecisionSummary::content)
                .isEqualTo("출시일을 확정한다.");
        assertThat(result.actions()).singleElement()
                .extracting(MeetingHistoryResult.ActionSummary::title)
                .isEqualTo("배포 체크리스트 작성");
    }

    /* 상류 계약 연결 전의 null과 실제 빈 목록을 서로 다른 상태로 유지하는지 검증한다. */
    @Test
    @DisplayName("상류 미연결과 조회 결과 없음 상태를 구분한다")
    void distinguishesUnavailableFromEmptySummaries() {
        /* 기존 호환 생성자는 상류 계약이 아직 연결되지 않은 상태를 만든다. */
        MeetingHistoryResult unavailable = new MeetingHistoryResult(
                10L,
                20L,
                "주간 회의",
                MeetingStatus.DONE,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                LocalDateTime.of(2026, 8, 10, 11, 0),
                LocalDateTime.of(2026, 8, 10, 10, 0),
                LocalDateTime.of(2026, 8, 10, 11, 0),
                30L,
                List.of()
        );

        /* 상류 조회가 연결됐지만 항목이 없는 상태는 명시적인 빈 목록으로 만든다. */
        MeetingHistoryResult availableButEmpty = history(List.of(), List.of());

        /* 미연결은 null, 연결 후 0건은 빈 목록으로 구분돼야 한다. */
        assertThat(unavailable.decisions()).isNull();
        assertThat(unavailable.actions()).isNull();
        assertThat(availableButEmpty.decisions()).isEmpty();
        assertThat(availableButEmpty.actions()).isEmpty();
    }

    /* 테스트에 공통으로 사용할 완료 회의 히스토리 결과를 만든다. */
    private MeetingHistoryResult history(
            List<MeetingHistoryResult.DecisionSummary> decisions,
            List<MeetingHistoryResult.ActionSummary> actions
    ) {
        /* 요약 목록의 제공 상태만 테스트하므로 나머지 회의 값은 고정한다. */
        return new MeetingHistoryResult(
                10L,
                20L,
                "주간 회의",
                MeetingStatus.DONE,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                LocalDateTime.of(2026, 8, 10, 11, 0),
                LocalDateTime.of(2026, 8, 10, 10, 0),
                LocalDateTime.of(2026, 8, 10, 11, 0),
                30L,
                List.of(new MeetingHistoryResult.Attendee(30L, "모성진", "개발팀")),
                decisions,
                actions
        );
    }
}
