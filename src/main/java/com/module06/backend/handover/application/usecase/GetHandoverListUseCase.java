package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.util.List;

/*
    FR-HO-07 — 인수인계서 관리 목록 조회.
    FE 관리 리스트 3개(사원 본인 / 팀장 팀 / 오너)를 하나의 목록 엔드포인트로 받는다.
    스코프는 writerMemberId(본인) 또는 teamId(팀) 중 하나로 지정하고, status로 선택 필터한다.
    auth(B) 도입 전까지는 호출자 id를 파라미터로 직접 받는다(임시 계약).
*/
public interface GetHandoverListUseCase {

    List<HandoverSummary> list(HandoverListQuery query);

    record HandoverListQuery(
            Long writerMemberId,
            Long teamId,
            HandoverStatus status
    ) {
    }

    record HandoverSummary(
            Long id,
            Long writerMemberId,
            String writerName,
            String writerPosition,
            Long teamId,
            HandoverType handoverType,
            HandoverStatus status,
            LocalDate leaveStartAt,
            LocalDate leaveEndAt,
            LocalDate lastWorkingDay,
            int itemCount,
            int reassignRequiredCount,
            int reassignedCount
    ) {
    }
}
