package com.module06.backend.capture.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.ReviewActionCreatePort;
import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.port.out.ReviewActionCreatePort.ManualAction;
import com.module06.backend.capture.application.result.ReviewActionAdded;
import com.module06.backend.capture.application.usecase.AddReviewActionUseCase;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * RVW-03 · 액션 직접 추가.
 *
 * AI 가 놓친 할 일을 사람이 넣는다. 파이프라인이 못 뽑은 것은 검토 화면에 아예 나타나지 않으므로,
 * 이 경로가 없으면 사람은 빠진 것을 알아도 넣을 방법이 없다.
 *
 * <h2>만들어지는 액션은 이미 확정 상태다</h2>
 * `is_manual=true` · `review_status=HUMAN_CONFIRMED` 다. 검토는 **AI 가 낸 것을 사람이 보는**
 * 절차인데, 사람이 방금 직접 쓴 항목을 PENDING 으로 두면 자기가 쓴 것을 자기가 다시 검토하는
 * 화면이 된다.
 *
 * <h2>라벨을 남기지 않는다</h2>
 * review_log 에 적지 않는다. 그 표는 {AI 입력 → 사람이 인정한 정답} 쌍을 쌓는 자리인데
 * 수동 추가는 **AI 입력이 없어 쌍이 성립하지 않는다**(명세 RVW-03 · V5.9 주석). few-shot 예시로도
 * 예약하지 않는다 — 같은 이유다. gold set 라벨로는 유효하고, 그건 QLTY-01 이 회의 단위로 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddReviewActionService implements AddReviewActionUseCase {

    private static final String STATUS_HUMAN_CONFIRMED = "HUMAN_CONFIRMED";

    private final ReviewActionCreatePort reviewActionCreatePort;
    private final TranscriptRepository transcriptRepository;
    private final MeetingAccessGuard meetingAccessGuard;
    private final MeetingProjectProvider meetingProjectProvider;
    private final MeetingParticipantProvider meetingParticipantProvider;

    @Override
    @Transactional
    public ReviewActionAdded add(AddReviewActionCommand command) {
        meetingAccessGuard.requireAccessible(command.companyId(), command.meetingId());
        requireManualShape(command);
        requireInRoster(command);
        requireEvidenceInMeeting(command);

        /*
         * 프로젝트를 못 읽으면 만들지 않는다. action.project_id 는 NOT NULL 이고, 임의의
         * 프로젝트로 채우면 그 액션이 엉뚱한 보드에 꽂힌다(MeetingProjectProvider 주석).
         */
        long projectId = meetingProjectProvider.projectIdOf(command.meetingId())
                .orElseThrow(() -> new IllegalStateException(
                        "회의의 프로젝트를 읽을 수 없습니다. meetingId=" + command.meetingId()));

        long actionId = reviewActionCreatePort.createManual(new ManualAction(
                command.companyId(),
                command.meetingId(),
                projectId,
                command.assigneeMemberId(),
                command.teamId(),
                command.title(),
                command.detail(),
                command.dueDate(),
                command.evidenceTranscriptId()));

        log.info("액션 직접 추가 — meetingId={} actionId={} 담당자={} 부서={} 추가한사람={}",
                command.meetingId(), actionId, command.assigneeMemberId(), command.teamId(), command.requestedBy());

        return new ReviewActionAdded(actionId, true, STATUS_HUMAN_CONFIRMED);
    }

    /*
     * 담당자·기한은 필수다. 비워두면 아무의 보드에도 가지 않는 액션이 조용히 쌓인다.
     *
     * 2026-08-13 — teamId 는 담당자의 대체재다(이홍근 요청). ReviewValue.teamId(RVW-02)와
     * 같은 판단으로, 상호 배타적이고 최소 하나는 필수다 — 같은 액션이 사람 하나와 부서 하나를
     * 동시에 가질 수 없고(ActionTypeShapePolicy), 담당자·부서 둘 다 없으면 여전히 아무의
     * 보드에도 가지 않는다.
     */
    private void requireManualShape(AddReviewActionCommand command) {
        if (command.assigneeMemberId() != null && command.teamId() != null) {
            throw new BusinessException(CaptureErrorCode.REVIEW_ASSIGNEE_TEAM_CONFLICT);
        }
        if (command.assigneeMemberId() == null && command.teamId() == null) {
            throw new BusinessException(CaptureErrorCode.REVIEW_MANUAL_FIELD_REQUIRED);
        }
        if (command.dueDate() == null) {
            throw new BusinessException(CaptureErrorCode.REVIEW_MANUAL_FIELD_REQUIRED);
        }
    }

    /*
     * 근거 발화가 **이 회의의 것인지** 본다.
     *
     * 검증 없이 저장하면 다른 회의 — 나아가 다른 회사 — 의 발화 id 를 넣을 수 있고, 검토
     * 화면(RVW-01)은 그 id 로 `transcript_chunk` 를 조인해 원문을 그대로 인용한다.
     * **화면에 뿌려지는 순간이 유출이라 저장 전에 막는다**(#100 과 같은 자리).
     *
     * 근거가 없는 것(null)은 정상이다 — 회의록에서 집어 오지 않고 사람이 새로 쓴 할 일이
     * 그 모양이고, 명세의 요청 예시도 evidenceTranscriptId 가 null 이다. 그래서 "없으면 거절"이
     * 아니라 **"있으면 이 회의 것이어야 한다"** 로 막는다.
     */
    private void requireEvidenceInMeeting(AddReviewActionCommand command) {
        Long evidenceId = command.evidenceTranscriptId();
        if (evidenceId == null) {
            return;
        }
        if (!transcriptRepository.existsInMeeting(command.meetingId(), evidenceId)) {
            throw new BusinessException(CaptureErrorCode.REVIEW_EVIDENCE_NOT_IN_MEETING);
        }
    }

    /*
     * 담당자가 참석자 명단 안인지 본다 — RVW-02 의 판정과 같은 규칙이다.
     *
     * 두 경로가 같은 규칙을 써야 하는 이유: 여기만 열어두면 명단 밖 담당자를 넣는 방법이
     * "직접 추가"로 남는다. 회의에 없던 사람의 보드로 가는 액션은 어느 경로로 만들어졌든 같은
     * 문제다.
     *
     * 명단 밖 탈출구(personId=null)는 후보에서 뺀다. 그건 "명단에 없는 사람"을 나타내는
     * 자리이고 실제 사람이 아니다.
     *
     * teamId 로 만드는 액션은 대상이 아니다 — 담당자 개념이 없으므로(ActionTypeShapePolicy)
     * 참석자 명단 검사 자체가 성립하지 않는다. requireManualShape 가 이미 둘 중 하나만
     * 오도록 막아 뒀으므로 여기 오면 assigneeMemberId 가 null 이 아니라는 뜻이다.
     */
    private void requireInRoster(AddReviewActionCommand command) {
        if (command.teamId() != null) {
            return;
        }
        List<AiLayerPort.Participant> roster =
                meetingParticipantProvider.participantsOf(command.meetingId());

        boolean inRoster = roster.stream()
                .map(AiLayerPort.Participant::personId)
                .filter(Objects::nonNull)
                .anyMatch(command.assigneeMemberId()::equals);

        if (!inRoster) {
            throw new BusinessException(CaptureErrorCode.REVIEW_ASSIGNEE_NOT_IN_ROSTER);
        }
    }
}
