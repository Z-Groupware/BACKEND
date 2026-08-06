package com.module06.backend.capture.domain.model;

import java.time.LocalDate;

/*
 * L4 가 뽑은 assignment tuple — 이 제품의 핵심 산출물이다. "누가 · 무엇을 · 언제까지".
 *
 * 세 자리가 모두 비어 있을 수 있고, **그게 정상이다.**
 *   assigneeCandidateMemberId null  명단 밖 사람을 가리켰거나(unknown_person) 담당자가 정해지지 않았다
 *   dueDate                   null  회의에서 기한을 말하지 않았거나 기준일을 몰라 계산하지 않았다
 *   assigneeSource            null  담당자 판정 근거를 모른다
 * 빈 자리를 기본값으로 채우면 "AI 가 판단한 값"과 "우리가 채운 값"이 구분되지 않는다.
 * 채우는 것은 분배 시점의 일이고, 채웠다는 사실은 action.due_date_defaulted 가 기록한다.
 *
 * evidenceUtteranceId 는 필수다(근거 강제). 근거 없는 tuple 은 계층이 반환하지 않는다 —
 * 사람이 검토할 때 "정말 그런 말이 있었나"를 확인할 수 없는 배정은 검토가 불가능하다.
 */
public record AssignmentTuple(
        String title,
        Long assigneeCandidateMemberId,
        AssigneeSource assigneeSource,
        LocalDate dueDate,
        Long evidenceUtteranceId
) {
}
