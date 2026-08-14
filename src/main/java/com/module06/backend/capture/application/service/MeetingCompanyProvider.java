package com.module06.backend.capture.application.service;

import java.util.Optional;

/* 자동 분석에 필요한 회사 식별자와 회의 완료 여부를 D 소유 meeting 데이터에서 읽는다. */
public interface MeetingCompanyProvider {

    /* 회의가 없으면 비어 있는 값을 반환한다. */
    Optional<AutomaticAnalysisTarget> findAutomaticAnalysisTarget(long meetingId);

    /* STT 완료만으로 진행 중 회의를 분석하지 않도록 상태까지 함께 전달한다. */
    record AutomaticAnalysisTarget(long companyId, boolean completed) {
    }
}
