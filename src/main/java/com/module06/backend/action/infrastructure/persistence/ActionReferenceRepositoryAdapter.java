package com.module06.backend.action.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.module06.backend.action.domain.repository.ActionReferenceRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 ActionReferenceRepository 계약을 JPA로 구현하는 어댑터.
    이미 있는 참조 엔티티 두 개(ActionMeetingReferenceEntity·ProjectReferenceEntity)를
    findAllById로 일괄조회해 도메인 계약의 read model로 바꿔 넘긴다 — 엔티티 타입이
    application 계층으로 새어나가지 않게 하는 것이 이 어댑터의 존재 이유다.

    조회 대상 id가 비면 Spring Data 호출 자체를 건너뛴다. 빈 IN 절 쿼리를 날리지 않기 위함.

    연결된 클래스
    - ActionReferenceRepository             : 구현하는 도메인 계약
    - ActionMeetingReferenceRepository      : meeting 배치조회 위임 대상
    - SpringDataProjectReferenceRepository  : project 배치조회 위임 대상
    - SpringDataProjectAttachmentReferenceRepository : 팀 액션 상세(FR-AC-06)의 첨부파일 조회 위임 대상
*/
@Component
@RequiredArgsConstructor
public class ActionReferenceRepositoryAdapter implements ActionReferenceRepository {

    private final ActionMeetingReferenceRepository actionMeetingReferenceRepository;
    private final SpringDataProjectReferenceRepository springDataProjectReferenceRepository;
    private final SpringDataActionTeamReferenceRepository springDataActionTeamReferenceRepository;
    private final SpringDataMemberReferenceRepository springDataMemberReferenceRepository;
    private final SpringDataProjectAttachmentReferenceRepository springDataProjectAttachmentReferenceRepository;

    @Override
    public List<MeetingReference> findMeetingReferences(List<Long> meetingIds) {
        if (meetingIds.isEmpty()) {
            return List.of();
        }

        return actionMeetingReferenceRepository.findAllById(meetingIds).stream()
                .map(meeting -> new MeetingReference(
                        meeting.getId(),
                        meeting.getTeamId(),
                        meeting.getRelatedActionId(),
                        meeting.getTitle()
                ))
                .toList();
    }

    @Override
    public List<ProjectReference> findProjectReferences(List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }

        return springDataProjectReferenceRepository.findAllById(projectIds).stream()
                .map(project -> new ProjectReference(project.getId(), project.getDueDate(), project.getTag(), project.getName()))
                .toList();
    }

    @Override
    public boolean existsTeamInCompany(Long teamId, Long companyId) {
        return springDataActionTeamReferenceRepository.existsByIdAndCompanyId(teamId, companyId);
    }

    @Override
    public boolean existsMemberInCompany(Long memberId, Long companyId) {
        return springDataMemberReferenceRepository.existsByIdAndCompanyId(memberId, companyId);
    }

    // 담당자 이름 배치 조회
    @Override
    public List<MemberReference> findMemberReferences(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }

        return springDataMemberReferenceRepository.findAllById(memberIds).stream()
                .map(member -> new MemberReference(member.getId(), member.getName()))
                .toList();
    }

    // 소속팀 이름 배치 조회
    @Override
    public List<TeamReference> findTeamReferences(List<Long> teamIds) {
        if (teamIds.isEmpty()) {
            return List.of();
        }

        return springDataActionTeamReferenceRepository.findAllById(teamIds).stream()
                .map(team -> new TeamReference(team.getId(), team.getName()))
                .toList();
    }

    // FR-AC-06 팀 액션 상세 — 소속 프로젝트 첨부파일 목록. 상세는 단건 조회라 배치가 필요 없다.
    @Override
    public List<AttachmentReference> findProjectAttachments(Long projectId) {
        return springDataProjectAttachmentReferenceRepository.findAllByProjectId(projectId).stream()
                .map(attachment -> new AttachmentReference(
                        attachment.getId(),
                        attachment.getFileName(),
                        attachment.getFileUrl(),
                        attachment.getFileSize(),
                        attachment.getCreatedAt()
                ))
                .toList();
    }

    // 2026-08-10 — 다운로드 URL 발급용 단건 조회.
    @Override
    public Optional<AttachmentReference> findProjectAttachmentById(Long attachmentId, Long projectId) {
        return springDataProjectAttachmentReferenceRepository.findByIdAndProjectId(attachmentId, projectId)
                .map(attachment -> new AttachmentReference(
                        attachment.getId(),
                        attachment.getFileName(),
                        attachment.getFileUrl(),
                        attachment.getFileSize(),
                        attachment.getCreatedAt()
                ));
    }
}
