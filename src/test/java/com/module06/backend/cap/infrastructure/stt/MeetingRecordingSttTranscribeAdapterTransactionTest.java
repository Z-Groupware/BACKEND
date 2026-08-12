package com.module06.backend.cap.infrastructure.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.capture.application.port.in.CreateSttBlockPort;

/*
 * CodeRabbit 지적(Major) — ManualRecordingService(클래스 레벨 @Transactional)가 이미 연 트랜잭션
 * 안에서 MeetingRecordingSttTranscribeAdapter.triggerWholeFileStt를 부르는데, 그 안에서 위임하는
 * CreateSttBlockPort의 실 구현(SttBlockCreationService)도 @Transactional(REQUIRED)이라 기본
 * 전파로 두면 호출자 트랜잭션에 합류한다 — sttJobPort.submit() 실패 시 호출자 트랜잭션 전체가
 * rollback-only로 마킹되고, 호출자가 예외를 잡아도 커밋 시 UnexpectedRollbackException으로 죽는다
 * (MeetingStorageUsageWriter와 동일 함정). 단위 테스트(서비스를 직접 new)로는 Spring 프록시를 안
 * 타서 이 전파 문제를 재현할 수 없다는 지적도 있었다 — 그래서 실제 @SpringBootTest 컨텍스트에서
 * 진짜 프록시를 태운다.
 *
 * MeetingRecordingSttTranscribeAdapter 자체는 @Profile("prod") 전용이라, "prod" 프로필을 활성화하는
 * 대신(다른 prod 전용 빈까지 끌려옴) 이 테스트에서만 별도 빈으로 등록해 프록시 적용 여부만 검증한다
 * (컴포넌트 스캔/프로필과 무관하게, @Bean으로 등록된 빈도 @Transactional 어드바이저의 대상이 된다).
 */
@SpringBootTest
@DisplayName("MeetingRecordingSttTranscribeAdapter — 호출자 트랜잭션 격리(REQUIRES_NEW) 검증")
class MeetingRecordingSttTranscribeAdapterTransactionTest {

    @TestConfiguration
    static class FailingSttConfig {
        // @Primary — 실 빈(SttBlockCreationService)도 컨텍스트에 이미 있어(다른 컴포넌트가
        // CreateSttBlockPort를 정상 의존하므로 제거할 수 없다), 이 테스트 컨텍스트 안에서만
        // 우선권을 준다.
        @Bean
        @Primary
        CreateSttBlockPort failingCreateSttBlockPort() {
            return command -> {
                throw new RuntimeException("STT 제출 실패(가정) — sttJobPort.submit 실패 재현");
            };
        }

        // @Primary — 기본(!prod) 프로필에서는 MeetingRecordingSttStubAdapter가 이미
        // MeetingRecordingSttPort 빈으로 떠 있어, 같은 타입 빈이 둘이 되는 걸 이걸로 해소한다.
        @Bean
        @Primary
        MeetingRecordingSttTranscribeAdapter testMeetingRecordingSttTranscribeAdapter(
                CreateSttBlockPort failingCreateSttBlockPort) {
            return new MeetingRecordingSttTranscribeAdapter(failingCreateSttBlockPort);
        }
    }

    @Autowired
    private MeetingRecordingSttTranscribeAdapter adapter;

    @Autowired
    private CaptureUploadStateRepository captureUploadStateRepository;

    /*
     * ManualRecordingService.registerManualRecording의 실제 순서를 그대로 재현한다 — 바깥
     * @Transactional 메서드 안에서 STT 트리거를 부르고 예외를 잡은 뒤, 그 뒤에도 같은(바깥)
     * 트랜잭션으로 DB 쓰기를 계속한다. REQUIRES_NEW가 없었다면 이 마지막 save()가 커밋 시점에
     * UnexpectedRollbackException으로 죽었을 것이다.
     */
    @Test
    @Transactional
    @DisplayName("STT 트리거가 실패해도 호출자 트랜잭션은 rollback-only로 마킹되지 않는다")
    void callerTransactionSurvivesSttFailure() {
        // ManualRecordingService.triggerWholeFileSttBestEffort와 동일하게 예외를 잡고 넘어간다.
        try {
            adapter.triggerWholeFileStt(800L, "recordings/org-1/meeting-800/recording.ogg");
        } catch (RuntimeException ignored) {
            // best-effort — 여기서 흡수한다.
        }

        // 그 뒤에도 같은(바깥) 트랜잭션에서 DB 쓰기가 정상 동작해야 한다 — REQUIRES_NEW가 없었다면
        // 위 save() 자체는 성공해도, 이 메서드가 끝나며 테스트 트랜잭션을 커밋/롤백하려는 시점에
        // rollback-only 마킹 때문에 UnexpectedRollbackException이 났을 것이다.
        assertThatCode(() ->
                captureUploadStateRepository.save(CaptureUploadState.startWithRecorder(800L, 7L)))
                .doesNotThrowAnyException();
        assertThat(captureUploadStateRepository.findByMeetingId(800L)).isPresent();
    }
}
