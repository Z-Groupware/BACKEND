package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.application.usecase.GetHandoverListUseCase.HandoverSummary;

import java.util.List;

/**
 * 오너·어드민 전용 "귀속 대기"(팀장 오프보딩 후 신규 팀장 미지정) 목록 조회.
 * 스코프는 클라이언트가 못 정한다 — 컨트롤러가 토큰의 companyId를 주입한다.
 */
public interface GetPendingAttributionListUseCase {

    List<HandoverSummary> listPendingAttribution(Long companyId);
}
