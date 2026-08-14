package com.module06.backend.handover.application.usecase;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.handover.domain.model.Handover;

import java.time.LocalDateTime;

public interface FinalizeHandoverUseCase {

    // 갭12: 권한 검증은 컨트롤러/B(auth) 책임. E는 검증된 승인자(approverId/approverName)를 감사로 저장만 한다.
    Handover finalize(Long handoverId, AuthPrincipal approver, LocalDateTime finalizedAt);
}
