package com.module06.backend.cap.application.service;

import org.springframework.stereotype.Component;

import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.repository.CapCaptureSessionReferenceRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * 캡처 세션이 ACTIVE일 때만 통과시키는 공용 검증 — presign(CaptureUploadService)과 complete의
 * 실제 DB 쓰기(CompletePartUploadWriter) 양쪽에서 쓴다.
 *
 * <h2>complete 쪽엔 왜 두 번 부르나(CodeRabbit 지적)</h2>
 * CapCaptureSessionReferenceRepository.findStatus는 잠금 없는 단순 조회라, 확인 시점과 실제 DB
 * 쓰기 시점 사이에 세션이 PAUSED/ENDED로 바뀌어도 업로드가 그대로 진행될 수 있다. completePartUpload는
 * 이 확인 뒤에 S3 HEAD(objectMatches, 동기 네트워크 호출)를 거치므로 그 구간이 특히 넓다 — 그래서
 * CompletePartUploadWriter.write(실제 저장 트랜잭션) 맨 앞에서 한 번 더 확인해 창을 좁힌다.
 *
 * <h2>완전한 동기화는 하지 않는다</h2>
 * capture_session을 SELECT ... FOR UPDATE로 잠그는 것까지 가면 cap이 D(meeting) 소유 행을 직접
 * 잠그게 되는데, 이건 cap이 D 데이터를 읽기 전용으로만 참조한다는 이 코드베이스 전체의 도메인
 * 경계 원칙을 깨는 큰 변화라 별도 논의 없이는 하지 않는다 — 두 번 확인으로 창을 좁히는 선까지만
 * best-effort로 막는다(레이스가 터져도 최악은 경계에서 청크 한두 개가 잘못 카운트되는 정도).
 */
@Component
class CaptureSessionActiveGuard {

    private final CapCaptureSessionReferenceRepository captureSessionReferenceRepository;

    CaptureSessionActiveGuard(CapCaptureSessionReferenceRepository captureSessionReferenceRepository) {
        this.captureSessionReferenceRepository = captureSessionReferenceRepository;
    }

    // ACTIVE만 통과시킨다 — PAUSED는 CAP-020(일시정지)으로, 그 외(ENDED/세션없음)는 CAP-022로
    // 구분해서 던진다. PAUSED만 별도 코드를 유지하는 이유는 프론트가 "일시정지 중이니 재개하라"는
    // 안내를 CAP-020에 이미 붙여뒀을 수 있어서다 — ENDED/세션없음은 재개 대상이 아니므로 다른
    // 코드로 명확히 분리한다.
    void requireActive(Long meetingId) {
        String status = captureSessionReferenceRepository.findStatus(meetingId).orElse(null);
        if ("PAUSED".equals(status)) {
            throw new BusinessException(CapErrorCode.CAP_CAPTURE_PAUSED);
        }
        if (!"ACTIVE".equals(status)) {
            throw new BusinessException(CapErrorCode.CAP_CAPTURE_SESSION_NOT_ACTIVE);
        }
    }
}
