package com.module06.backend.cap.application.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard.ViewerContext;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.ProjectTeamReferenceRepository;

/*
 * CAP 공통 회의 열람권한 판정(canView)의 순서·조건을 단위로 검증한다: 참석자 → 같은 회사
 * owner/admin(cross-tenant 차단) → 프로젝트 멤버(팀 배정) → 전부 아니면 거부. PlaybackUrlService(CAP-14),
 * GetCaptionsService(CAP-12)가 공통으로 이 가드에 위임하므로, 여기서 조합을 한 번에 검증해두면
 * 서비스별 테스트는 자신의 고유 로직(404/녹음 유무 등)만 신경 쓰면 된다.
 */
@DisplayName("CAP 공통 회의 열람권한 가드")
class CapMeetingAccessGuardTest {

    private static final Long MEETING_ID = 500L;
    private static final Long COMPANY_ID = 1L;
    private static final Long PROJECT_ID = 12L;

    @Test
    @DisplayName("참석자는 role 무관하게 허용한다")
    void attendeeAllowed() {
        CapMeetingAccessGuard guard = guard(true, false);

        assertThat(guard.canView(MEETING_ID, viewer(7L, 2L, 99L, "MEMBER", false))).isTrue();
    }

    @Test
    @DisplayName("참석 안 했어도 같은 회사 owner는 허용한다")
    void sameCompanyOwnerAllowed() {
        CapMeetingAccessGuard guard = guard(false, false);

        assertThat(guard.canView(MEETING_ID, viewer(7L, COMPANY_ID, null, "OWNER", false))).isTrue();
    }

    @Test
    @DisplayName("참석 안 했어도 같은 회사 admin은 허용한다")
    void sameCompanyAdminAllowed() {
        CapMeetingAccessGuard guard = guard(false, false);

        assertThat(guard.canView(MEETING_ID, viewer(7L, COMPANY_ID, null, "MEMBER", true))).isTrue();
    }

    @Test
    @DisplayName("다른 회사 owner/admin은 프로젝트 멤버가 아니면 거부한다(cross-tenant 차단)")
    void otherCompanyOwnerRejectedWithoutProjectMembership() {
        CapMeetingAccessGuard guard = guard(false, false);

        assertThat(guard.canView(MEETING_ID, viewer(7L, 2L, null, "OWNER", false))).isFalse();
        assertThat(guard.canView(MEETING_ID, viewer(7L, 2L, null, "MEMBER", true))).isFalse();
    }

    @Test
    @DisplayName("참석 안 하고 owner/admin도 아니어도, 회의 프로젝트에 자기 팀이 배정돼 있으면 허용한다")
    void projectMemberAllowed() {
        CapMeetingAccessGuard guard = guard(false, true);

        assertThat(guard.canView(MEETING_ID, viewer(7L, 2L, 9L, "MEMBER", false))).isTrue();
    }

    @Test
    @DisplayName("팀이 배정돼 있지 않으면 프로젝트 멤버로 인정하지 않는다")
    void unassignedTeamRejected() {
        CapMeetingAccessGuard guard = guard(false, false);

        assertThat(guard.canView(MEETING_ID, viewer(7L, 2L, 9L, "MEMBER", false))).isFalse();
    }

    @Test
    @DisplayName("teamId가 없으면 프로젝트 멤버 판정 자체를 건너뛰고 거부한다")
    void nullTeamIdRejected() {
        CapMeetingAccessGuard guard = guard(false, true);

        assertThat(guard.canView(MEETING_ID, viewer(7L, 2L, null, "MEMBER", false))).isFalse();
    }

    @Test
    @DisplayName("회의가 프로젝트에 태그돼 있지 않으면 프로젝트 멤버 판정은 항상 거부한다")
    void meetingWithoutProjectRejectsProjectMembership() {
        MeetingReferenceRepository meetingRef = meetingRef(false, Optional.empty());
        ProjectTeamReferenceRepository projectTeamRef = (projectId, teamId) -> {
            throw new AssertionError("프로젝트가 없는 회의는 프로젝트 배정 조회 자체를 하면 안 됩니다.");
        };
        CapMeetingAccessGuard guard = new CapMeetingAccessGuard(meetingRef, projectTeamRef);

        assertThat(guard.canView(MEETING_ID, viewer(7L, 2L, 9L, "MEMBER", false))).isFalse();
    }

    /* isAttendee는 canView와 달리 owner/admin·프로젝트 멤버 우회가 없는 순수 참석자 게이트인지 검증한다
       (presign/complete, 조립 트리거가 씀). */
    @Test
    @DisplayName("isAttendee는 참석자만 허용하고 owner/admin·프로젝트 멤버는 우회하지 못한다")
    void isAttendeeHasNoBypass() {
        CapMeetingAccessGuard guard = guard(true, true);

        assertThat(guard.isAttendee(MEETING_ID, 7L)).isTrue();

        CapMeetingAccessGuard nonAttendeeGuard = guard(false, true);
        assertThat(nonAttendeeGuard.isAttendee(MEETING_ID, 7L)).isFalse();
    }

    /* isHost는 담당자(host_member_id)만 허용하는지 검증한다(수동 업로드가 씀). */
    @Test
    @DisplayName("isHost는 회의 담당자만 허용한다")
    void isHostOnlyForHost() {
        MeetingReferenceRepository meetingRef = new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return true;
            }

            @Override
            public boolean isAttendee(Long meetingId, Long memberId) {
                return true;
            }

            @Override
            public boolean isHost(Long meetingId, Long memberId) {
                return memberId.equals(3L);
            }

            @Override
            public Optional<Long> findCompanyId(Long meetingId) {
                return Optional.of(COMPANY_ID);
            }

            @Override
            public int countAttendees(Long meetingId) {
                return 0;
            }

            @Override
            public Optional<Long> findProjectId(Long meetingId) {
                return Optional.empty();
            }
        };
        CapMeetingAccessGuard guard = new CapMeetingAccessGuard(meetingRef, (projectId, teamId) -> false);

        assertThat(guard.isHost(MEETING_ID, 3L)).isTrue();
        assertThat(guard.isHost(MEETING_ID, 9L)).isFalse();
    }

    /* isSameCompany는 회의가 없거나 회사가 다르면 거부하는지 검증한다(삭제가 씀 — cross-tenant 차단). */
    @Test
    @DisplayName("isSameCompany는 회의의 회사와 일치할 때만 허용한다")
    void isSameCompanyMatchesMeetingCompany() {
        CapMeetingAccessGuard guard = guard(false, false);

        assertThat(guard.isSameCompany(MEETING_ID, COMPANY_ID)).isTrue();
        assertThat(guard.isSameCompany(MEETING_ID, 999L)).isFalse();
    }

    // 참석자 여부와, 배정된 팀이 이 프로젝트에 있는지를 지정해 가드를 조립한다. 회의는 회사 1·프로젝트 12 소속.
    private CapMeetingAccessGuard guard(boolean attendee, boolean teamAssigned) {
        MeetingReferenceRepository meetingRef = meetingRef(attendee, Optional.of(PROJECT_ID));
        ProjectTeamReferenceRepository projectTeamRef =
                (projectId, teamId) -> teamAssigned && projectId.equals(PROJECT_ID);
        return new CapMeetingAccessGuard(meetingRef, projectTeamRef);
    }

    private MeetingReferenceRepository meetingRef(boolean attendee, Optional<Long> projectId) {
        return new MeetingReferenceRepository() {
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
                return Optional.of(COMPANY_ID);
            }

            @Override
            public int countAttendees(Long meetingId) {
                return 0;
            }

            @Override
            public Optional<Long> findProjectId(Long meetingId) {
                return projectId;
            }
        };
    }

    private ViewerContext viewer(Long memberId, Long companyId, Long teamId, String role, boolean isAdmin) {
        return new ViewerContext(memberId, companyId, teamId, role, isAdmin);
    }
}
