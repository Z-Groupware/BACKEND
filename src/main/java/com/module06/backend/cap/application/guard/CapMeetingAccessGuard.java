package com.module06.backend.cap.application.guard;

import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.ProjectTeamReferenceRepository;
import org.springframework.stereotype.Component;

/* comment.
    CAP 공통 회의 열람권한 판정 — CodeRabbit이 여러 PR(CAP-11·12)에서 반복 지적한 "각 서비스가
    canView를 각자 구현"하던 중복을 하나로 모은다(PlaybackUrlService·GetCaptionsService가 각자
    갖고 있던 canView와 동일 로직 + 프로젝트 멤버 확대).

    판정 순서:
    1. 참석자면 무조건 허용(참석자는 회의=회사 소속이 이미 보장됨).
    2. 참석자가 아니면, 그 뒤로는 무조건 같은 회사여야 한다(cross-tenant 차단) — 다른 회사면
       owner/admin이든 프로젝트 멤버든 여기서 거부.
    3. 같은 회사인 참석자 외 요청자 중 owner/admin이면 허용(감독 열람).
    4. owner/admin도 아니면, 회의가 태그된 프로젝트에 자기 팀이 배정돼 있으면 허용(프로젝트
       멤버 열람 — CAP-10/CAP-12/CAP-14에서 계속 미뤄뒀던 확대). team_id는 요청자 자신의
       회사 소속 팀이지만, project_team 조인만으로는 그 프로젝트가 같은 회사 것인지 보장되지
       않으므로(데이터 이상 시 IDOR) 2번의 회사 스코프 확인을 먼저 통과해야만 이 분기에 온다.
    5. 전부 아니면 거부.
*/
@Component
public class CapMeetingAccessGuard {

    private final MeetingReferenceRepository meetingReferenceRepository;
    private final ProjectTeamReferenceRepository projectTeamReferenceRepository;

    public CapMeetingAccessGuard(MeetingReferenceRepository meetingReferenceRepository,
                                 ProjectTeamReferenceRepository projectTeamReferenceRepository) {
        this.meetingReferenceRepository = meetingReferenceRepository;
        this.projectTeamReferenceRepository = projectTeamReferenceRepository;
    }

    public boolean canView(Long meetingId, ViewerContext viewer) {
        if (meetingReferenceRepository.isAttendee(meetingId, viewer.memberId())) {
            return true;
        }
        if (!isSameCompany(meetingId, viewer.companyId())) {
            return false;
        }
        if (viewer.isOwnerOrAdmin()) {
            return true;
        }
        return viewer.teamId() != null && isProjectMember(meetingId, viewer.teamId());
    }

    /**
     * 참석자 여부(순수 게이트) — presign/complete(캡처 업로드, CaptureUploadService), 녹음 종료/조립
     * (CAP-05, RecordingAssemblyService)가 쓴다. canView와 달리 owner/admin·프로젝트 멤버 우회가 없다 —
     * 이 두 엔드포인트는 "진행 중인 녹음 세션에 실제로 참여 중인가"를 묻는 것이지 "열람 가능한가"를
     * 묻는 게 아니라서, 감독 열람·프로젝트 협업 열람까지 넓히면 안 된다.
     */
    public boolean isAttendee(Long meetingId, Long memberId) {
        return meetingReferenceRepository.isAttendee(meetingId, memberId);
    }

    /**
     * 회의 담당자(Host) 여부 — 수동 업로드(CAP-10, ManualRecordingService)가 쓴다. 참석자보다 좁은
     * 게이트(회의 담당자 본인만) — [녹음] 버튼 대신 파일을 직접 첨부하는 대체 경로라 임의 참석자가
     * 아니라 담당자로 좁힌다.
     */
    public boolean isHost(Long meetingId, Long memberId) {
        return meetingReferenceRepository.isHost(meetingId, memberId);
    }

    /**
     * 같은 회사 소속인지(cross-tenant 차단) — 녹음 삭제(CAP-15, DeleteRecordingService)가 쓴다.
     * 역할 게이트(owner/admin)는 컨트롤러 @PreAuthorize가 이미 하므로, 여기서는 순수 회사 스코프만 본다.
     */
    public boolean isSameCompany(Long meetingId, Long companyId) {
        return meetingReferenceRepository.findCompanyId(meetingId)
                .map(meetingCompanyId -> meetingCompanyId.equals(companyId))
                .orElse(false);
    }

    private boolean isProjectMember(Long meetingId, Long teamId) {
        return meetingReferenceRepository.findProjectId(meetingId)
                .map(projectId -> projectTeamReferenceRepository.isTeamAssignedToProject(projectId, teamId))
                .orElse(false);
    }

    /**
     * 열람권한 판정용 요청자 신원. role/isAdmin은 identity 도메인 소유값이라 cap은 enum 의존 없이
     * 토큰 클레임 그대로 받아 문자열로 판정한다(GetPlaybackUrlUseCase.Requester와 동일 관용구).
     */
    public record ViewerContext(Long memberId, Long companyId, Long teamId, String role, boolean isAdmin) {
        public boolean isOwnerOrAdmin() {
            return "OWNER".equals(role) || isAdmin;
        }
    }
}
