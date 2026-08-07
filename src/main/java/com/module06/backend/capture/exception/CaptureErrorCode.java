package com.module06.backend.capture.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

import com.module06.backend.global.exception.ErrorCode;

/*
 * 캡처 파이프라인(도메인 A) 전용 에러 코드다.
 *
 * 도메인별 enum 으로 분리해 담당자 간 파일 충돌을 막는다(MR-·PJ- 규약과 같은 방식).
 * 코드 문자열은 API 명세의 표를 그대로 따른다.
 */
@Getter
@AllArgsConstructor
public enum CaptureErrorCode implements ErrorCode {

    /* ANLZ-01 — 같은 회의의 분석이 이미 돌고 있다. 오류처럼 보이지만 중복 방어가 동작한 것이다. */
    ANALYSIS_ALREADY_RUNNING(HttpStatus.CONFLICT, "MEETING_409_3", "분석이 이미 진행 중입니다."),

    /* ANLZ-01 — 이미 완료된 분석을 force 없이 다시 돌리려 한 경우다. 재과금을 막는다. */
    ANALYSIS_ALREADY_DONE(HttpStatus.CONFLICT, "MEETING_409_4", "이미 분석이 완료된 회의입니다."),

    /* CAP-06 · ANLZ-03 — 아직 분석하지 않았거나 다른 회사 회의다. 타 회사 리소스는 403이 아니라 404로 존재를 숨긴다. */
    SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "ANLZ-001", "회의 요약이 없습니다."),

    /*
     * 캡처 파이프라인 공통 — 그 회사에 속한 회의가 아니다(없는 회의도 여기로 온다).
     *
     * 403 이 아니라 404 다. 403 이면 "이 회의는 존재하지만 당신 것이 아니다"가 새어 나가고,
     * id 를 훑어 남의 회사 회의 개수를 셀 수 있다. 없는 회의와 접근 불가를 같은 응답으로 덮는다.
     */
    MEETING_NOT_ACCESSIBLE(HttpStatus.NOT_FOUND, "MEETING_404_1", "회의를 찾을 수 없습니다."),

    /*
     * 계층 호출이 실패해 분석이 멈췄다.
     *
     * 502 인 이유 — 우리 요청이 잘못된 것이 아니라 뒤에 있는 AI 서버가 응답하지 못한 것이다.
     * 500 으로 내리면 이 저장소의 버그와 구분되지 않고, 알람이 엉뚱한 사람에게 간다.
     */
    ANALYSIS_LAYER_FAILED(HttpStatus.BAD_GATEWAY, "ANLZ-002", "분석 계층 호출에 실패했습니다."),

    /*
     * RVW-02 — 그 회의의 액션이 아니다(없는 액션도 여기로 온다).
     *
     * MEETING_NOT_ACCESSIBLE 과 같은 이유로 404 다. 관문은 회의까지만 보므로, 회의는 내 것인데
     * actionId 만 남의 것을 넣는 경로가 남는다 — 그 시도에 403 을 주면 "그 액션은 존재한다"가
     * 새어 나가고 id 를 훑어 남의 회사 액션 개수를 셀 수 있다.
     */
    REVIEW_ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETING_404_2", "액션을 찾을 수 없습니다."),

    /*
     * RVW-02 — 수정·반려에 사유 코드가 없다(또는 CONFIRM 에 사유가 붙었다).
     *
     * 422 로 막는 이유 — 사유 없는 라벨은 **어느 계층을 고쳐야 할지 가리키지 못해 라벨로서
     * 쓸모가 없다.** 그런 행이 섞이면 정확도 조사를 처음부터 다시 해야 하고, 지나간 회의는
     * 다시 만들 수 없으므로 그 조사는 불가능하다. DB CHECK(CK_REVIEW_LOG_REASON)가 같은 규칙을
     * 강제하지만, 여기서 먼저 막아야 사용자에게 이유가 보인다 — DB 까지 내려가면 500 이 된다.
     *
     * ⚠ UNPROCESSABLE_ENTITY 가 이 스프링 버전에서 deprecated 경고를 낸다. 대체 상수
     * (UNPROCESSABLE_CONTENT)가 아직 없어 그대로 쓴다 — 명세가 요구하는 코드가 422 다.
     */
    REVIEW_REASON_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "MEETING_422_3", "수정·반려에는 사유 코드가 필요합니다."),

    /*
     * RVW-02 — 참석자 명단에 없는 담당자로 고치려 했다.
     *
     * 사람이 고른 값이라 신뢰할 만해 보이지만, 드롭다운 밖의 id 를 직접 보내는 경로가 남아
     * 있다. 명단 밖 담당자를 넣으면 그 액션은 회의에 참석하지 않은 사람의 보드로 가고,
     * **그 값이 정답 라벨로 학습된다** — 틀린 배정을 AI 에게 가르치는 셈이다.
     */
    REVIEW_ASSIGNEE_NOT_IN_ROSTER(HttpStatus.UNPROCESSABLE_ENTITY, "MEETING_422_1", "참석자 명단에 없는 담당자입니다."),

    /*
     * RVW-02 — CONFIRM 에 담당자·기한을 함께 보냈다.
     *
     * CONFIRM 은 "AI 값이 그대로 정답"이라는 뜻이고, 그래서 라벨의 human_value 가 null 이다
     * (llm_output 과 같다는 표시). 값을 함께 받아 반영하면 **액션은 바뀌는데 라벨에는 그 변경이
     * 남지 않는다** — 나중에 그 행을 보면 "AI 가 맞혔다"고 읽히지만 실제 정답은 사람이 고친
     * 다른 값이다. 그 라벨은 틀린 값을 정답으로 가르치고, 정확도 숫자도 실제보다 높게 나온다.
     *
     * 값을 무시하지 않고 422 로 되돌리는 이유 — 무시하면 사람이 고친 담당자가 조용히 사라져
     * 화면과 DB 가 갈린다. 값을 고쳤다면 MODIFY 로 사유와 함께 보내야 한다.
     */
    REVIEW_CONFIRM_WITH_VALUE(HttpStatus.UNPROCESSABLE_ENTITY, "MEETING_422_4",
            "무수정 승인에는 담당자·기한을 함께 보낼 수 없습니다."),

    /*
     * RVW-04 — AI 가 만든 액션을 삭제하려 했다.
     *
     * **AI 생성 액션은 지우는 것이 아니라 반려(RVW-02 REJECT)한다.** 지우면 라벨이 사라진다 —
     * "AI 가 이런 걸 뽑았고 사람이 아니라고 했다"는 쌍이 개선의 재료인데, 행이 없어지면 그
     * 사실 자체가 없던 일이 된다. 지나간 회의는 다시 만들 수 없어 복구도 불가능하다.
     */
    REVIEW_DELETE_AI_ACTION(HttpStatus.CONFLICT, "MEETING_409_7", "AI 생성 액션은 반려로 처리해야 합니다."),
    /*
     * RVW-03 — 직접 추가에 담당자·기한이 없다.
     *
     * **수동 추가 경로는 담당자를 강제한다**(C 도메인과 2026-08-07 합의). AI 분배 경로는
     * 담당자 미정을 허용하는데, 그건 검토 화면에서 사람이 고르는 후속 단계가 있기 때문이다.
     * 직접 추가는 사람이 이미 그 화면 앞에 있으므로 그 자리에서 정하는 것이 맞고, 비워두면
     * 아무의 보드에도 가지 않는 액션이 조용히 쌓인다.
     *
     * 기한도 같다. action.due_date 가 NOT NULL 이고, AI 경로처럼 프로젝트 마감일로 대신
     * 채우지 않는다 — 사람이 직접 넣는 자리에서 서버가 날짜를 지어내면 그게 사용자가 정한
     * 기한인지 기본값인지 화면에서 구분되지 않는다.
     */
    REVIEW_MANUAL_FIELD_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "MEETING_422_5",
            "직접 추가에는 담당자와 기한이 필요합니다."),

    /*
     * RVW-03 — 근거 발화가 이 회의의 것이 아니다.
     *
     * **다른 회의(다른 회사)의 발화 id 를 넣는 경로를 막는다.** 검증 없이 저장하면 그 id 가
     * 액션에 박히고, 검토 화면(RVW-01)은 그 id 로 원문을 조인해 보여준다 — 남의 회의 발화
     * 내용이 우리 화면에 인용된다. 화면에 뿌려지는 순간 유출이라 저장 전에 막는다(#100).
     *
     * 근거를 아예 넣지 않는 것(null)은 정상이다. 회의록에서 집어 오지 않고 사람이 새로 쓴
     * 할 일이 그 모양이고, 명세의 요청 예시도 evidenceTranscriptId 가 null 이다.
     */
    REVIEW_EVIDENCE_NOT_IN_MEETING(HttpStatus.UNPROCESSABLE_ENTITY, "MEETING_422_6",
            "이 회의의 발화가 아닙니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
