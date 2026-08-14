package com.module06.backend.metering.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.metering.application.port.in.StorageQuotaPort;

/*
 * MeetingRecordingSttTranscribeAdapterTransactionTest와 동일한 함정을 검증하되, 그 테스트와 달리
 * 테스트 메서드 자체를 @Transactional로 두지 않는다 — Spring Test의 @Transactional은 테스트가
 * 끝날 때 항상 rollback()을 호출하지 commit()을 안 하므로, "커밋하려 했는데 rollback-only라 실패"
 * 를 뜻하는 UnexpectedRollbackException은 테스트 트랜잭션 안에서는 애초에 재현되지 않는다
 * (실제로 REQUIRES_NEW를 빼고 돌려봐도 두 테스트 모두 통과해버리는 것으로 확인됨 — 2026-08-14).
 *
 * 대신 CaptureUploadService.issuePartUploadUrls의 실제 모양(같은 클래스 안에서 @Transactional
 * 메서드가 getStatus 호출→예외 흡수→DB 쓰기 후 정상 반환)을 그대로 재현하는 실 Spring 빈
 * (QuotaCheckHarness)을 별도로 만들어, 테스트 메서드 밖(비-트랜잭션 컨텍스트)에서 그 빈을 부른다.
 * 그래야 harness의 @Transactional이 메서드 종료 시 진짜 commit()을 시도하고, REQUIRES_NEW가
 * 없으면 그 commit()에서 UnexpectedRollbackException이 실제로 던져진다.
 */
@SpringBootTest
@DisplayName("StorageMeteringService.getStatus — 호출자 트랜잭션 격리(REQUIRES_NEW) 검증")
class StorageMeteringServiceTransactionTest {

    // CaptureUploadService.issuePartUploadUrls의 관련 부분만 재현하는 실 Spring 빈 — @Transactional
    // 어드바이저를 실제로 태워야 하므로 테스트 안에서 new로 만들지 않고 컨텍스트에 빈으로 등록한다.
    @Service
    static class QuotaCheckHarness {
        private final StorageQuotaPort storageQuotaPort;
        private final CaptureUploadStateRepository captureUploadStateRepository;

        QuotaCheckHarness(StorageQuotaPort storageQuotaPort,
                          CaptureUploadStateRepository captureUploadStateRepository) {
            this.storageQuotaPort = storageQuotaPort;
            this.captureUploadStateRepository = captureUploadStateRepository;
        }

        // CaptureUploadService.issuePartUploadUrls와 동일한 순서: 한도 조회(예외면 흡수) → DB 쓰기 → 정상 반환.
        @Transactional
        void checkQuotaThenWrite(Long companyId, Long meetingId, Long callerId) {
            try {
                storageQuotaPort.getStatus(companyId);
            } catch (RuntimeException ignored) {
                // fail-open — CaptureUploadService.isOverStorageQuota와 동일하게 여기서 흡수한다.
            }
            captureUploadStateRepository.save(CaptureUploadState.startWithRecorder(meetingId, callerId));
        }
    }

    @TestConfiguration
    static class HarnessConfig {
        @Bean
        QuotaCheckHarness quotaCheckHarness(StorageQuotaPort storageQuotaPort,
                                            CaptureUploadStateRepository captureUploadStateRepository) {
            return new QuotaCheckHarness(storageQuotaPort, captureUploadStateRepository);
        }
    }

    @Autowired
    private QuotaCheckHarness quotaCheckHarness;

    @Autowired
    private CaptureUploadStateRepository captureUploadStateRepository;

    /*
     * companyId=901은 CompanyStoragePlan을 저장한 적 없다 → getStatus가 MT_STORAGE_PLAN_NOT_FOUND를
     * 던진다. harness가 그걸 흡수하고 이어서 DB에 쓴 뒤 정상 반환해야 한다 — 테스트 메서드 자체는
     * @Transactional이 아니므로, harness 메서드가 끝나는 순간 Spring이 진짜 commit()을 시도한다.
     * REQUIRES_NEW가 없었다면 이 commit()이 UnexpectedRollbackException으로 실패했을 것이다.
     */
    @Test
    @DisplayName("한도 미설정 회사의 조회 실패가 호출자 트랜잭션의 커밋을 막지 않는다")
    void callerTransactionCommitsSuccessfullyAfterMissingPlan() {
        assertThatCode(() -> quotaCheckHarness.checkQuotaThenWrite(901L, 901L, 7L))
                .doesNotThrowAnyException();

        assertThat(captureUploadStateRepository.findByMeetingId(901L)).isPresent();
    }
}
