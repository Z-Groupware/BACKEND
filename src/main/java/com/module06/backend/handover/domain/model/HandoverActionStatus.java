package com.module06.backend.handover.domain.model;

import java.util.Set;

/**
 * 인계 항목의 액션 상태 스냅샷(String)에 대한 완료 판정 규칙. 도메인 지식이라 도메인에 둔다.
 * 재분배 필요 여부(reassignRequired) 판정(HandoverService)과 공백 요약(HandoverPackageService)이 공유.
 */
public final class HandoverActionStatus {

    private static final Set<String> COMPLETE_STATUSES = Set.of("DONE", "COMPLETE", "COMPLETED", "완료");

    private HandoverActionStatus() {
    }

    public static boolean isComplete(String status) {
        return status != null && COMPLETE_STATUSES.contains(status.trim().toUpperCase());
    }
}
