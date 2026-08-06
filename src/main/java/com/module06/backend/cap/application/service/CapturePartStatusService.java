package com.module06.backend.cap.application.service;

import com.module06.backend.cap.application.usecase.GetPartUploadStatusUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 청크 업로드 상태 조회(CAP-08)의 실제 로직. 회의 존재 → 녹음자 검증 → 현재 세그먼트의 누락 순번 계산.
// 새로고침/크래시 후 재접속 시 프론트가 "어디까지 올라가 있는지" 확인해 이어서 업로드하기 위한 읽기 전용 조회.
@Service
@Transactional(readOnly = true)
public class CapturePartStatusService implements GetPartUploadStatusUseCase {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final CaptureUploadStateRepository captureUploadStateRepository;
    private final RecordingPartRepository recordingPartRepository;

    public CapturePartStatusService(MeetingReferenceRepository meetingReferenceRepository,
                                    CaptureUploadStateRepository captureUploadStateRepository,
                                    RecordingPartRepository recordingPartRepository) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.captureUploadStateRepository = captureUploadStateRepository;
        this.recordingPartRepository = recordingPartRepository;
    }

    @Override
    public Result getPartUploadStatus(Long meetingId, Long callerId) {
        // 회의 존재 확인(404) → 참석자 확인(403) → 상태행 확인 → 녹음자 검증(403).
        if (!meetingReferenceRepository.existsById(meetingId)) {
            throw new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND);
        }
        // presign/complete와 동일한 회의 접근 확인 — 참석자 명단에서 빠진 옛 녹음자가 상태행만으로
        // 조회하는 걸 막는다(recorder_person_id 비교만으론 못 거르는 IDOR 갭 보완).
        if (!meetingReferenceRepository.isAttendee(meetingId, callerId)) {
            throw new BusinessException(CapErrorCode.CAP_NOT_ATTENDEE);
        }
        // 상태행이 없다는 건 presign이 한 번도 없었다는 뜻 — 녹음자로 배정된 사람 자체가 없으므로 caller도 녹음자가 아니다.
        CaptureUploadState state = captureUploadStateRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new BusinessException(CapErrorCode.CAP_NOT_CURRENT_RECORDER));
        state.verifyRecorder(callerId);

        // 현재 세그먼트에서 업로드 기록이 있는 순번들 → 1..lastSeq 중 빠진 순번이 missingSeqs.
        List<Integer> presentSeqs = recordingPartRepository.findSeqsInSegment(meetingId, state.getSegmentSeq());
        List<Integer> missingSeqs = computeMissingSeqs(state.getLastSeq(), presentSeqs);

        // resumeFromSeq: 다음에 이어서 올릴 순번. gapMs: 라이브 조회에선 계산 소스가 없어 0(조립 시 채워짐).
        int resumeFromSeq = state.getLastSeq() + 1;
        long gapMs = 0L;

        return new Result(state.getSegmentSeq(), state.getLastSeq(), missingSeqs,
                state.getBlocksFormed(), resumeFromSeq, gapMs);
    }

    // 1..lastSeq 중 업로드 기록이 없는 순번을 오름차순으로 모은다. lastSeq=0이면 빈 목록.
    private List<Integer> computeMissingSeqs(int lastSeq, List<Integer> presentSeqs) {
        Set<Integer> present = new HashSet<>(presentSeqs);
        List<Integer> missing = new ArrayList<>();
        for (int seq = 1; seq <= lastSeq; seq++) {
            if (!present.contains(seq)) {
                missing.add(seq);
            }
        }
        return missing;
    }
}
