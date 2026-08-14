package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.command.IssueManualRecordingUploadUrlCommand;
import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.port.out.MeetingRecordingSttPort;
import com.module06.backend.cap.application.usecase.IssueManualRecordingUploadUrlUseCase;
import com.module06.backend.cap.application.usecase.RegisterManualRecordingUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.command.ReportMeetingStorageUsageCommand;
import com.module06.backend.metering.application.port.in.ReportMeetingStorageUsagePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 수동 녹음 업로드(CAP-10): 회의 존재 → Host 검증 → s3Key 검증 → 중복 제출 확인 → recording 등록 → 단일 블록 STT 트리거.
// [녹음] 버튼 대신 외부(온라인 회의 등)에서 녹음한 파일을 직접 첨부하는 대체 경로다.
//
// presign(issueManualRecordingUploadUrl)과 register(registerManualRecording)를 한 클래스에 같이 둔다 —
// CaptureUploadService가 presign/complete를 함께 갖는 것과 동일 이유다. 두 메서드가 같은 s3Key 접두
// 규칙(recordings/org-{orgId}/meeting-{meetingId}/)을 공유해야 하는데, 갈라두면 한쪽만 바뀌었을 때
// 정상 발급된 키를 register가 거부하는 자기부정이 생긴다.
@Service
@Transactional
public class ManualRecordingService implements RegisterManualRecordingUseCase, IssueManualRecordingUploadUrlUseCase {

    private static final Logger log = LoggerFactory.getLogger(ManualRecordingService.class);

    // metering report의 revision(생성=1, 삭제=2 — DeleteRecordingService.DELETE_REVISION 참고).
    // 벽시계(clock.millis())를 쓰지 않는다(CodeRabbit 지적) — 같은 밀리초에 겹치거나 시계가
    // 뒤로 조정되면 진짜 최신 report가 "더 안 큼"으로 오판돼 무시될 수 있다. recording.meeting_id가
    // UNIQUE(V4.2.1)라 이 회의의 recording 생성은 조립·수동등록 중 하나로 평생 단 한 번만 일어나고,
    // 삭제는 그 뒤에만 가능하다 — 그래서 "생성=1, 삭제=2" 상수만으로 항상 올바른 순서를 나타낸다.
    private static final long CREATE_REVISION = 1L;

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CapMeetingAccessGuard accessGuard;
    private final RecordingRepository recordingRepository;
    private final MeetingRecordingSttPort meetingRecordingSttPort;
    private final ReportMeetingStorageUsagePort reportMeetingStorageUsagePort;
    private final CapObjectStoragePort capObjectStoragePort;

    public ManualRecordingService(MeetingReferenceRepository meetingReferenceRepository,
                                  CapMeetingAccessGuard accessGuard,
                                  RecordingRepository recordingRepository,
                                  MeetingRecordingSttPort meetingRecordingSttPort,
                                  ReportMeetingStorageUsagePort reportMeetingStorageUsagePort,
                                  CapObjectStoragePort capObjectStoragePort) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.accessGuard = accessGuard;
        this.recordingRepository = recordingRepository;
        this.meetingRecordingSttPort = meetingRecordingSttPort;
        this.reportMeetingStorageUsagePort = reportMeetingStorageUsagePort;
        this.capObjectStoragePort = capObjectStoragePort;
    }

    // presign 자체는 로컬 서명 계산이라 네트워크 호출이 없다(CaptureUploadService.issuePartUploadUrls와 동일 이유) —
    // @Transactional 안에 있어도 커넥션을 오래 붙들지 않는다.
    @Override
    public IssueManualRecordingUploadUrlUseCase.Result issueManualRecordingUploadUrl(
            IssueManualRecordingUploadUrlCommand command) {
        Long companyId = meetingReferenceRepository.findCompanyId(command.meetingId())
                .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND));

        if (!accessGuard.isHost(command.meetingId(), command.callerId())) {
            throw new BusinessException(CapErrorCode.CAP_NOT_HOST);
        }

        // fileName 자체를 검증한다(register의 최종 키 검증과 별개) — "..".pdf 같은 이름이 그대로
        // 키에 들어가면, 정상 발급된 자기 키인데도 register의 접두 검증이 경로 조작으로 오판해
        // 거부한다(ProjectAttachmentService.issueUploadUrl과 동일 취지 — #313).
        String fileName = command.fileName();
        if (fileName == null || fileName.isBlank() || fileName.contains("..")) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
        }

        // 이미 제출된 녹음이 있으면 여기서 먼저 막는다 — 헛되이 presigned URL을 발급하고
        // 클라이언트가 업로드까지 끝낸 뒤에야 register(CAP-10)에서 409를 받는 것보다,
        // 업로드 시작 전에 바로 알려주는 편이 낫다(회의당 recording은 평생 단 하나).
        if (recordingRepository.existsByMeetingId(command.meetingId())) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_ALREADY_SUBMITTED);
        }

        String s3Key = buildManualRecordingS3Key(companyId, command.meetingId(), fileName);
        CapObjectStoragePort.IssuedPartUploadUrl issued =
                capObjectStoragePort.issuePartUploadUrl(s3Key, command.contentType());
        return new IssueManualRecordingUploadUrlUseCase.Result(s3Key, issued.presignedUrl(), issued.expiresInSeconds());
    }

    // registerManualRecording의 접두 검증과 반드시 같은 규칙이어야 한다 — 한 클래스에 같이 둔
    // 이유가 이 일치를 보장하기 위해서다. 접두 자체는 s3KeyPrefix()로 한 곳에서만 만든다.
    //
    // 원본 파일명을 그대로 키에 넣지 않는다 — 한글·공백 등 비-ASCII 문자가 presigned PUT URL의
    // SigV4 서명 대상 경로에 그대로 들어가면, 클라이언트가 그 URL을 다시 인코딩/디코딩하는 과정에서
    // 서명 시점 바이트와 실제 PUT 요청 바이트가 어긋나 SignatureDoesNotMatch(403)가 난다. 대신
    // UUID+확장자만 키에 쓰고, 원본 파일명은 별도로 받아 표시용 메타(Recording.fileName)에만 남긴다.
    private String buildManualRecordingS3Key(Long companyId, Long meetingId, String fileName) {
        return s3KeyPrefix(companyId, meetingId) + UUID.randomUUID() + fileExtensionOf(fileName);
    }

    // "recording.ogg" → ".ogg". 확장자가 없으면 빈 문자열(마지막 세그먼트를 통째로 확장자로
    // 오인하지 않는다 — "recording"처럼 점이 없는 이름도 유효한 입력이다).
    private String fileExtensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? "" : fileName.substring(dotIndex);
    }

    // recordings/org-{orgId}/meeting-{meetingId}/ — 영구 보관 경로 접두. 발급(presign)과
    // 검증(register) 양쪽이 이 메서드 하나만 본다 — 문자열을 두 곳에 따로 적으면 한쪽만 바뀌었을 때
    // 정상 발급된 키를 register가 경로 조작으로 오판해 거부하는 자기부정이 생긴다.
    private String s3KeyPrefix(Long companyId, Long meetingId) {
        return "recordings/org-%d/meeting-%d/".formatted(companyId, meetingId);
    }

    @Override
    public RegisterManualRecordingUseCase.Result registerManualRecording(RegisterManualRecordingCommand command) {
        // 회의 존재 확인(404) + companyId 확보(키 검증용).
        Long companyId = meetingReferenceRepository.findCompanyId(command.meetingId())
                .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND));

        // Host만 가능(403). 참석자보다 좁은 권한 — 회의 담당자 본인만.
        if (!accessGuard.isHost(command.meetingId(), command.callerId())) {
            throw new BusinessException(CapErrorCode.CAP_NOT_HOST);
        }

        // s3Key는 서버가 기대하는 영구 보관 경로 접두로 시작해야 한다 — 타 회사·타 회의·임시
        // (stt-temp) 경로나 경로 조작(..)을 막는다(PR1 complete의 키 검증과 동일 취지).
        String expectedPrefix = s3KeyPrefix(companyId, command.meetingId());
        String s3Key = command.s3Key();
        if (s3Key == null || s3Key.contains("..") || !s3Key.startsWith(expectedPrefix)
                || s3Key.length() == expectedPrefix.length()) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
        }
        // 표시용 파일명은 더 이상 s3Key(UUID 기반)에서 파싱하지 않는다 — 클라이언트가 별도로 보낸다.
        String fileName = command.fileName();
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
        }

        // 이미 제출된 녹음이 있으면 중복 제출 거부(409).
        if (recordingRepository.existsByMeetingId(command.meetingId())) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_ALREADY_SUBMITTED);
        }

        // 요청 본문 sizeBytes를 그대로 믿지 않는다(CaptureUploadService.completePartUpload와 동일 취지) —
        // 실제로 그 크기로 업로드가 됐는지 S3 HEAD로 확인한다. 없거나 크기가 다르면 클라이언트가
        // 업로드도 안 하고(또는 presign만 받고) 등록을 시도한 것이므로, 존재하지 않는 파일을 등록해
        // 가짜 저장 용량이 report되거나 존재하지 않는 객체로 STT가 트리거되는 것을 막는다.
        if (!capObjectStoragePort.objectMatches(s3Key, command.sizeBytes())) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_NOT_UPLOADED);
        }

        // 메타 등록(durationSec은 파이프라인이 async로 채움) + 단일 블록 STT 트리거(스텁).
        // sttTriggered=true로 등록 — 트리거 직후엔 stt_block이 아직 0건이라, CAP-15 삭제 차단 판정이
        // 이를 "완료"가 아니라 "진행 중"으로 읽게 한다.
        recordingRepository.save(Recording.register(command.meetingId(), fileName, s3Key, command.sizeBytes(), true));
        triggerWholeFileSttBestEffort(command.meetingId(), s3Key);
        reportStorageUsageBestEffort(companyId, command.meetingId(), command.sizeBytes());

        // durationMs=0(파이프라인이 채움), status="DONE"(제출=완료 리터럴, meeting.status는 D 소유라 쓰지 않음).
        return new RegisterManualRecordingUseCase.Result(command.meetingId(), 0L, command.sizeBytes(), "DONE");
    }

    // 단일 블록 STT를 트리거한다 — 실패해도 등록 자체를 되돌리지 않는다(MeetingRecordingSttPort의
    // 계약이 best-effort임을 SttBlockCreationService 주석이 명시 — "cap의 트리거는 best-effort라
    // 실패하면 이번 회차 블록 생성을 건너뛴 것으로 취급한다"). 실 어댑터로 교체하기 전(스텁)엔 예외를
    // 던진 적이 없어 이 보호막이 없어도 드러나지 않았을 뿐이다.
    private void triggerWholeFileSttBestEffort(Long meetingId, String s3Key) {
        try {
            meetingRecordingSttPort.triggerWholeFileStt(meetingId, s3Key);
        } catch (RuntimeException e) {
            log.error("단일 블록 STT 트리거 실패 — 녹음 등록은 완료됨, STT만 누락. meetingId={}", meetingId, e);
        }
    }

    // metering에 현재 사용량을 report한다 — 실패해도 등록 자체를 되돌리지 않는다
    // (CaptureUploadService.reportStorageUsageBestEffort와 동일 패턴).
    private void reportStorageUsageBestEffort(Long companyId, Long meetingId, long usedBytes) {
        try {
            Long projectId = meetingReferenceRepository.findProjectId(meetingId)
                    .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND));
            reportMeetingStorageUsagePort.report(new ReportMeetingStorageUsageCommand(
                    companyId, projectId, meetingId, usedBytes, CREATE_REVISION));
        } catch (RuntimeException e) {
            log.error("저장 용량 미터링 기록 실패 — 녹음 등록은 완료됨, 원장만 누락. meetingId={}", meetingId, e);
        }
    }
}
