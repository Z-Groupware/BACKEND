package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.usecase.GetPlaybackUrlUseCase;
import com.module06.backend.cap.application.usecase.GetPlaybackUrlUseCase.Requester;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CAP-14 재생 URL 발급 서비스의 열람 권한(참석자 / 같은 회사 owner·admin)·녹음 존재·presigned GET/duration 규칙을 검증한다.
 * 회의는 회사 1 소속으로 고정(findCompanyId=1).
 */
@DisplayName("CAP-14 재생 URL 발급 서비스")
class PlaybackUrlServiceTest {

    private static final String KEY = "recordings/org-1/meeting-500/recording.ogg";

    /* 참석자면 role 무관하게 발급되는지 검증한다. */
    @Test
    @DisplayName("참석자는 재생 URL을 발급받는다")
    void attendeeGetsUrl() {
        PlaybackUrlService service = service(true, Optional.of(recording(3612)));

        GetPlaybackUrlUseCase.Result result = service.getPlaybackUrl(500L, member(7L, 1L));

        assertThat(result.url()).isEqualTo("https://stub/playback/" + KEY);
        assertThat(result.expiresIn()).isEqualTo(10800);
        assertThat(result.durationMs()).isEqualTo(3_612_000L);
    }

    /* 참석 안 한 일반 멤버(같은 회사)는 CAP-010으로 거절되는지 검증한다. */
    @Test
    @DisplayName("참석 안 한 일반 멤버는 CAP-010으로 거절한다")
    void rejectsNonAttendeeMember() {
        PlaybackUrlService service = service(false, Optional.of(recording(100)));

        assertErrorCode(() -> service.getPlaybackUrl(500L, member(7L, 1L)), "CAP-010");
    }

    /* 참석 안 했어도 같은 회사 owner는 발급받는지 검증한다(감독 열람). */
    @Test
    @DisplayName("같은 회사 owner는 참석 안 해도 발급받는다")
    void sameCompanyOwnerGetsUrl() {
        PlaybackUrlService service = service(false, Optional.of(recording(100)));

        GetPlaybackUrlUseCase.Result result =
                service.getPlaybackUrl(500L, new Requester(7L, 1L, "OWNER", false));

        assertThat(result.url()).isEqualTo("https://stub/playback/" + KEY);
    }

    /* 참석 안 했어도 같은 회사 admin은 발급받는지 검증한다. */
    @Test
    @DisplayName("같은 회사 admin은 참석 안 해도 발급받는다")
    void sameCompanyAdminGetsUrl() {
        PlaybackUrlService service = service(false, Optional.of(recording(100)));

        GetPlaybackUrlUseCase.Result result =
                service.getPlaybackUrl(500L, new Requester(7L, 1L, "MEMBER", true));

        assertThat(result.url()).isEqualTo("https://stub/playback/" + KEY);
    }

    /* 다른 회사 owner는 참석 안 했으면 CAP-010으로 거절되는지 검증한다(cross-tenant 차단). */
    @Test
    @DisplayName("다른 회사 owner/admin은 거절한다(cross-tenant 차단)")
    void rejectsOtherCompanyOwner() {
        PlaybackUrlService service = service(false, Optional.of(recording(100)));

        // 회의는 회사 1인데 요청자는 회사 2의 owner
        assertErrorCode(() -> service.getPlaybackUrl(500L, new Requester(7L, 2L, "OWNER", false)), "CAP-010");
        // 회사 2의 admin도 마찬가지
        assertErrorCode(() -> service.getPlaybackUrl(500L, new Requester(7L, 2L, "MEMBER", true)), "CAP-010");
    }

    /* 녹음본이 없으면 CAP-016으로 거절되는지 검증한다(권한 통과 후). */
    @Test
    @DisplayName("녹음본이 없으면 CAP-016으로 거절한다")
    void rejectsWhenRecordingMissing() {
        PlaybackUrlService service = service(true, Optional.empty());

        assertErrorCode(() -> service.getPlaybackUrl(500L, member(7L, 1L)), "CAP-016");
    }

    /* duration이 아직 안 채워졌으면(null) durationMs가 0인지 검증한다. */
    @Test
    @DisplayName("duration 미채움이면 durationMs는 0이다")
    void durationZeroWhenNotComputed() {
        PlaybackUrlService service = service(true, Optional.of(Recording.register(500L, "recording.ogg", KEY, 100L)));

        assertThat(service.getPlaybackUrl(500L, member(7L, 1L)).durationMs()).isZero();
    }

    // 일반 멤버 요청자(회사 지정).
    private Requester member(Long memberId, Long companyId) {
        return new Requester(memberId, companyId, "MEMBER", false);
    }

    // duration_sec 지정 녹음본.
    private Recording recording(int durationSec) {
        return Recording.restore(1L, 500L, "recording.ogg", KEY, 15_000_000L, durationSec, null, null);
    }

    // 참석 여부·녹음본을 지정해 서비스를 조립한다. 회의는 회사 1 소속, 스토리지는 키 기반 가짜 GET URL을 돌려준다.
    private PlaybackUrlService service(boolean attendee, Optional<Recording> recording) {
        MeetingReferenceRepository meetingRef = new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return true;
            }

            @Override
            public boolean isAttendee(Long meetingId, Long memberId) {
                return attendee;
            }

            @Override
            public boolean isHost(Long meetingId, Long memberId) {
                return false;
            }

            @Override
            public Optional<Long> findCompanyId(Long meetingId) {
                return Optional.of(1L);
            }
        };
        RecordingRepository recordingRepo = new RecordingRepository() {
            @Override
            public Recording save(Recording r) {
                return r;
            }

            @Override
            public boolean existsByMeetingId(Long meetingId) {
                return recording.isPresent();
            }

            @Override
            public Optional<Recording> findByMeetingId(Long meetingId) {
                return recording;
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
            }
        };
        CapObjectStoragePort storage = new CapObjectStoragePort() {
            @Override
            public IssuedPartUploadUrl issuePartUploadUrl(String s3Key, String contentType) {
                throw new AssertionError("재생 경로에서 업로드 URL은 호출되면 안 됩니다.");
            }

            @Override
            public IssuedPlaybackUrl issuePlaybackUrl(String s3Key) {
                return new IssuedPlaybackUrl("https://stub/playback/" + s3Key, 10800);
            }

            @Override
            public void deleteRecording(String s3Key) {
            }
        };
        return new PlaybackUrlService(meetingRef, recordingRepo, storage);
    }

    // 실행 결과가 예상 서비스 오류 코드인지 검증한다.
    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
