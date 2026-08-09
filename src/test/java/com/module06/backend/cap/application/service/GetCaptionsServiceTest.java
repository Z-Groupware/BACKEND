package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard.ViewerContext;
import com.module06.backend.cap.application.usecase.GetCaptionsUseCase;
import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.CaptionChunkRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.ProjectTeamReferenceRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CAP-12 자막 전체 조회 서비스의 회의 존재·열람 권한(참석자 / 같은 회사 owner·admin / 프로젝트 멤버)·
 * 시간순 반환 규칙을 검증한다. 회의는 회사 1·프로젝트 12 소속으로 고정.
 */
@DisplayName("CAP-12 자막 전체 조회 서비스")
class GetCaptionsServiceTest {

    private static final Long PROJECT_ID = 12L;

    /* 회의가 없으면 CAP-002로 거절하는지 검증한다. */
    @Test
    @DisplayName("회의가 없으면 CAP-002로 거절한다")
    void rejectsWhenMeetingMissing() {
        GetCaptionsService service = service(false, false, List.of(), List.of());

        assertErrorCode(() -> service.getCaptions(500L, member(7L, 1L)), "CAP-002");
    }

    /* 참석 안 하고, owner/admin도 아니고, 프로젝트 멤버도 아니면 CAP-010으로 거절되는지 검증한다. */
    @Test
    @DisplayName("참석 안 한 일반 멤버는 CAP-010으로 거절한다")
    void rejectsNonAttendeeMember() {
        GetCaptionsService service = service(true, false, List.of(), List.of());

        assertErrorCode(() -> service.getCaptions(500L, member(7L, 1L)), "CAP-010");
    }

    /* 참석자는 자막을 시간순으로 전부 조회하는지 검증한다. */
    @Test
    @DisplayName("참석자는 자막 전체를 시간순으로 조회한다")
    void attendeeGetsCaptionsInOrder() {
        CaptionChunk first = CaptionChunk.receive(500L, 7L, 12, 184_000, 186_200,
                "다음 스프린트 목표부터", new BigDecimal("-18.4"));
        GetCaptionsService service = service(true, true, List.of(first), List.of());

        GetCaptionsUseCase.Result result = service.getCaptions(500L, member(7L, 1L));

        assertThat(result.captions()).hasSize(1);
        GetCaptionsUseCase.CaptionItem item = result.captions().get(0);
        assertThat(item.seq()).isEqualTo(12);
        assertThat(item.personId()).isEqualTo(7L);
        assertThat(item.startMs()).isEqualTo(184_000);
        assertThat(item.endMs()).isEqualTo(186_200);
        assertThat(item.text()).isEqualTo("다음 스프린트 목표부터");
        assertThat(item.rms()).isEqualByComparingTo("-18.4");
    }

    /* 참석 안 했어도 같은 회사 owner는 조회 가능한지 검증한다(감독 열람). */
    @Test
    @DisplayName("같은 회사 owner는 참석 안 해도 조회한다")
    void sameCompanyOwnerGetsCaptions() {
        GetCaptionsService service = service(true, false, List.of(), List.of());

        GetCaptionsUseCase.Result result = service.getCaptions(500L, new ViewerContext(7L, 1L, null, "OWNER", false));

        assertThat(result.captions()).isEmpty();
    }

    /* 참석 안 했어도 같은 회사 admin은 조회 가능한지 검증한다. */
    @Test
    @DisplayName("같은 회사 admin은 참석 안 해도 조회한다")
    void sameCompanyAdminGetsCaptions() {
        GetCaptionsService service = service(true, false, List.of(), List.of());

        GetCaptionsUseCase.Result result = service.getCaptions(500L, new ViewerContext(7L, 1L, null, "MEMBER", true));

        assertThat(result.captions()).isEmpty();
    }

    /* 다른 회사 owner/admin은 참석 안 했으면 CAP-010으로 거절되는지 검증한다(cross-tenant 차단). */
    @Test
    @DisplayName("다른 회사 owner/admin은 거절한다(cross-tenant 차단)")
    void rejectsOtherCompanyOwner() {
        GetCaptionsService service = service(true, false, List.of(), List.of());

        assertErrorCode(() -> service.getCaptions(500L, new ViewerContext(7L, 2L, null, "OWNER", false)), "CAP-010");
        assertErrorCode(() -> service.getCaptions(500L, new ViewerContext(7L, 2L, null, "MEMBER", true)), "CAP-010");
    }

    /* 참석 안 하고 owner/admin도 아니어도, 같은 회사이고 회의 프로젝트에 자기 팀이 배정돼 있으면
       조회하는지 검증한다. 회의는 회사 1 소속이므로 요청자도 회사 1이어야 한다. */
    @Test
    @DisplayName("같은 회사 프로젝트 멤버는 참석 안 해도 조회한다")
    void projectMemberGetsCaptions() {
        GetCaptionsService service = service(true, false, List.of(), List.of(9L));

        GetCaptionsUseCase.Result result = service.getCaptions(500L, new ViewerContext(7L, 1L, 9L, "MEMBER", false));

        assertThat(result.captions()).isEmpty();
    }

    /* 팀이 이 회의의 프로젝트에 배정돼 있지 않으면 프로젝트 멤버로 인정되지 않는지 검증한다. */
    @Test
    @DisplayName("다른 프로젝트 팀은 거절한다")
    void rejectsUnassignedTeam() {
        GetCaptionsService service = service(true, false, List.of(), List.of(9L));

        assertErrorCode(() -> service.getCaptions(500L, new ViewerContext(7L, 1L, 99L, "MEMBER", false)), "CAP-010");
    }

    /* 팀이 배정돼 있어도 요청자가 다른 회사면 거절하는지 검증한다(프로젝트 멤버 경로의 cross-tenant 차단,
       CodeRabbit 지적 — project_team 조인만으로는 회사 스코프가 보장되지 않는다). */
    @Test
    @DisplayName("팀은 배정돼 있어도 다른 회사면 거절한다")
    void rejectsProjectMemberFromOtherCompany() {
        GetCaptionsService service = service(true, false, List.of(), List.of(9L));

        assertErrorCode(() -> service.getCaptions(500L, new ViewerContext(7L, 2L, 9L, "MEMBER", false)), "CAP-010");
    }

    // 일반 멤버 요청자(회사 지정, 팀 없음).
    private ViewerContext member(Long memberId, Long companyId) {
        return new ViewerContext(memberId, companyId, null, "MEMBER", false);
    }

    // 회의 존재/참석 여부·자막 목록·프로젝트에 배정된 팀 목록을 지정해 서비스를 조립한다. 회의는 회사 1·프로젝트 12 소속.
    private GetCaptionsService service(boolean meetingExists, boolean attendee, List<CaptionChunk> captions,
                                       List<Long> assignedTeamIds) {
        MeetingReferenceRepository meetingRef = new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return meetingExists;
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

            @Override
            public int countAttendees(Long meetingId) {
                return 0;
            }

            @Override
            public Optional<Long> findProjectId(Long meetingId) {
                return Optional.of(PROJECT_ID);
            }
        };
        ProjectTeamReferenceRepository projectTeamRef =
                (projectId, teamId) -> projectId.equals(PROJECT_ID) && assignedTeamIds.contains(teamId);
        CapMeetingAccessGuard accessGuard = new CapMeetingAccessGuard(meetingRef, projectTeamRef);

        CaptionChunkRepository captionChunkRepository = new CaptionChunkRepository() {
            @Override
            public List<CaptionChunk> saveAllSkippingDuplicates(List<CaptionChunk> chunks) {
                throw new AssertionError("조회 경로에서 저장은 호출되면 안 됩니다.");
            }

            @Override
            public List<CaptionChunk> findByMeetingId(Long meetingId) {
                return captions;
            }
        };
        return new GetCaptionsService(meetingRef, accessGuard, captionChunkRepository);
    }

    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
