package com.module06.backend.capture.presentation.api.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase.ReviewDecisionCommand;
import com.module06.backend.capture.domain.model.RejectReason;
import com.module06.backend.capture.domain.model.ReviewDecision;

/*
 * RVW-02 요청이다. 명세의 필드명을 그대로 따른다.
 *
 * <h2>사유의 필수 여부를 여기서 검사하지 않는다</h2>
 * REJECT 에는 필수이고 CONFIRM·MODIFY 에는 붙을 수 없는데, 그건 **필드 하나만 보고는 알 수
 * 없는 규칙**이다(decision 과의 조합이다). @AssertTrue 로 넣으면 응답 메시지가 필드 오류로
 * 나가 어느 규칙을 어겼는지 흐려지고, 명세가 정한 코드(422 MEETING_422_3)를 줄 수 없다.
 * 서비스가 판정하고 DB CHECK 가 마지막으로 막는다.
 *
 * <h2>value 는 고친 칸만 담는다</h2>
 * null 은 "안 고쳤다"이고 "비우라"가 아니다. 담당자 지우기는 검토 화면에 없는 동작이고,
 * PERSONAL 액션은 담당자가 필수다.
 *
 * <h2>2026-08-11 — rejectReason은 이제 REJECT 전용, MODIFY는 안 보낸다</h2>
 * 담당자·기한·제목·내용을 한 번에 여러 개 고쳐도 사유를 하나만 담을 자리가 없다는 설계
 * 공백이 있었다(이홍근 발견). value에 뭐가 채워졌는지 BE가 보고 필드별로 사유를 자동
 * 유도하는 쪽으로 바꿔서, FE는 MODIFY일 때 rejectReason 필드 자체를 비워 보내면 된다.
 */
public record ReviewDecisionRequest(
        @NotNull(message = "판정(decision)은 필수입니다.")
        ReviewDecision decision,
        RejectReason rejectReason,
        ReviewValue value
) {

    public ReviewDecisionCommand toCommand(long companyId, long meetingId, long actionId, long confirmedBy) {
        return new ReviewDecisionCommand(
                companyId,
                meetingId,
                actionId,
                confirmedBy,
                decision,
                rejectReason,
                value != null ? value.assigneeMemberId() : null,
                value != null ? value.teamId() : null,
                value != null ? value.dueDate() : null,
                value != null ? value.title() : null,
                value != null ? value.detail() : null,
                value != null ? value.plannedStartDate() : null);
    }

    /*
     * 사람이 고친 값. 담당자·기한·제목·내용 중 고친 칸만 채운다(2026-08-11, title·detail
     * 추가 — 이홍근 요청). detail은 Action.description에 대응(ActionReviewResponse의
     * 기존 필드명과 통일).
     *
     * <h2>2026-08-12 — plannedStartDate 추가(#386 후속)</h2>
     * 예정 시작일이다. 나머지 넷과 성질이 다르다 — **AI 가 내지 않는 값**이라 "고친 칸"이
     * 아니라 "사람이 처음 정하는 칸"이다. 그래서 CONFIRM 과 함께 보낼 수 있고(다른 넷은
     * CONFIRM 에 실리면 422), 라벨(review_log)에 WRONG_* 사유가 만들어지지 않는다.
     *
     * 범위(익일 ~ 프로젝트 마감일)는 여기서 검사하지 않는다. 프로젝트 마감일은 action 도메인
     * 데이터라 DTO 가 알 수 없고, @Future 같은 단일 필드 제약으로는 상한을 표현할 수 없다 —
     * 사유 필수 여부를 여기서 안 보는 것과 같은 이유다(클래스 주석).
     *
     * <h2>2026-08-13 — teamId 추가(오너 회의 검토화면 부서선택)</h2>
     * assigneeMemberId 와 상호 배타적이다 — 같은 액션이 사람 하나와 부서 하나를 동시에 가질
     * 수 없다. 둘 다 채워 보내면 422(REVIEW_ASSIGNEE_TEAM_CONFLICT). CONFIRM 취급은
     * assigneeMemberId 와 같다(plannedStartDate 처럼 예외를 두지 않는다) — AI가 애초에
     * 부서를 낸 적이 없어도, 화면이 담당자 대신 부서를 보여주는 것뿐이라 "고치는" 성격이
     * 같기 때문이다.
     */
    public record ReviewValue(Long assigneeMemberId, Long teamId, LocalDate dueDate, String title, String detail,
                              LocalDate plannedStartDate) {
    }
}
