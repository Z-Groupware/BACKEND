package com.module06.backend.handover.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.command.FinalizeHandoverInsightsCommand;
import com.module06.backend.handover.application.port.out.ActionReassignPort;
import com.module06.backend.handover.application.port.out.HandoverInsightPort;
import com.module06.backend.handover.application.port.out.MeetingQueryPort;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.application.usecase.FinalizeHandoverInsightsUseCase;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.domain.model.HandoverInsight;
import com.module06.backend.handover.domain.model.HandoverInsightKind;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoverInsightFinalizeService implements FinalizeHandoverInsightsUseCase {

    private static final int MAX_PEOPLE_PER_ROLE = 2;

    // 스냅샷 직렬화는 앱의 web ObjectMapper 설정과 무관하게 고정 포맷이어야 하므로 모듈이 자체 보유.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final HandoverInsightPort handoverInsightPort;
    private final ObjectProvider<ActionReassignPort> actionReassignPortProvider;
    private final ObjectProvider<MeetingQueryPort> meetingQueryPortProvider;
    private final ObjectProvider<OrgQueryPort> orgQueryPortProvider;

    public HandoverInsightFinalizeService(
        HandoverInsightPort handoverInsightPort,
        ObjectProvider<ActionReassignPort> actionReassignPortProvider,
        ObjectProvider<MeetingQueryPort> meetingQueryPortProvider,
        ObjectProvider<OrgQueryPort> orgQueryPortProvider
    ) {
        this.handoverInsightPort = handoverInsightPort;
        this.actionReassignPortProvider = actionReassignPortProvider;
        this.meetingQueryPortProvider = meetingQueryPortProvider;
        this.orgQueryPortProvider = orgQueryPortProvider;
    }

    @Override
    @Transactional
    public void finalizeInsights(FinalizeHandoverInsightsCommand command) {
        ActionReassignPort actionReassignPort = required(actionReassignPortProvider, "ActionReassignPort");
        MeetingQueryPort meetingQueryPort = required(meetingQueryPortProvider, "MeetingQueryPort");
        OrgQueryPort orgQueryPort = required(orgQueryPortProvider, "OrgQueryPort");

        List<ActionReassignPort.HandoverableAction> handoverableActions =
            actionReassignPort.findHandoverableActions(command.departureMemberId());
        List<ActionReassignPort.TeamActionForDeparture> teamActions =
            actionReassignPort.findTeamActionsForDeparture(command.departureMemberId());

        InsightSource source = loadSource(command.departureMemberId(), handoverableActions, teamActions, meetingQueryPort);
        Map<Long, OrgQueryPort.MemberSummary> members = loadAskWhomMembers(
            command.departureMemberId(),
            handoverableActions,
            source,
            orgQueryPort
        );

        List<HandoverInsight> insights = new ArrayList<>();
        insights.add(newInsight(command.handoverId(), null, HandoverInsightKind.OWNERSHIP, ownershipPayload(source), 0));
        insights.add(newInsight(command.handoverId(), null, HandoverInsightKind.ORPHAN_ALERT, orphanPayload(command.departureMemberId(), teamActions, source), 1));

        int sortOrder = 2;
        for (ActionReassignPort.HandoverableAction action : handoverableActions) {
            insights.add(newInsight(
                command.handoverId(),
                action.actionId(),
                HandoverInsightKind.ASK_WHOM,
                askWhomPayload(command.departureMemberId(), action, source, members),
                sortOrder++
            ));
            insights.add(newInsight(
                command.handoverId(),
                action.actionId(),
                HandoverInsightKind.CONTEXT_TIMELINE,
                contextTimelinePayload(action, source),
                sortOrder++
            ));
        }

        handoverInsightPort.replaceAllForHandover(command.handoverId(), insights);
    }

    private static <T> T required(ObjectProvider<T> provider, String name) {
        T port = provider.getIfAvailable();
        if (port == null) {
            throw new BusinessException(HandoverErrorCode.HO_CONFLICT);
        }
        return port;
    }

    private InsightSource loadSource(
        Long departureMemberId,
        List<ActionReassignPort.HandoverableAction> handoverableActions,
        List<ActionReassignPort.TeamActionForDeparture> teamActions,
        MeetingQueryPort meetingQueryPort
    ) {
        Set<Long> projectIds = new LinkedHashSet<>();
        handoverableActions.stream()
            .map(ActionReassignPort.HandoverableAction::projectId)
            .filter(Objects::nonNull)
            .forEach(projectIds::add);
        teamActions.stream()
            .map(ActionReassignPort.TeamActionForDeparture::projectId)
            .filter(Objects::nonNull)
            .forEach(projectIds::add);

        Map<Long, List<MeetingQueryPort.ProjectMeeting>> meetingsByProject = new LinkedHashMap<>();
        for (Long projectId : projectIds) {
            List<MeetingQueryPort.ProjectMeeting> orderedMeetings = new ArrayList<>(meetingQueryPort.findProjectMeetingsOrdered(projectId));
            orderedMeetings.sort(Comparator.comparing(
                MeetingQueryPort.ProjectMeeting::startAt, Comparator.nullsLast(LocalDateTime::compareTo)));
            meetingsByProject.put(projectId, List.copyOf(orderedMeetings));
        }

        List<Long> meetingIds = meetingsByProject.values().stream()
            .flatMap(List::stream)
            .map(MeetingQueryPort.ProjectMeeting::meetingId)
            .distinct()
            .toList();
        Map<Long, List<MeetingQueryPort.MeetingAttendee>> attendeesByMeeting = meetingIds.isEmpty()
            ? Map.of()
            : meetingQueryPort.findMeetingAttendees(meetingIds).stream()
                .collect(Collectors.groupingBy(MeetingQueryPort.MeetingAttendee::meetingId));
        Map<Long, List<MeetingQueryPort.MeetingTopic>> topicsByMeeting = meetingIds.isEmpty()
            ? Map.of()
            : meetingQueryPort.findMeetingTopics(meetingIds).stream()
                .sorted(Comparator.comparing(MeetingQueryPort.MeetingTopic::sortOrder)
                    .thenComparing(MeetingQueryPort.MeetingTopic::topicId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.groupingBy(MeetingQueryPort.MeetingTopic::meetingId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, Long> assignedActionCountByProject = handoverableActions.stream()
            .filter(action -> action.projectId() != null)
            .collect(Collectors.groupingBy(ActionReassignPort.HandoverableAction::projectId, Collectors.counting()));

        return new InsightSource(departureMemberId, meetingsByProject, attendeesByMeeting, topicsByMeeting, assignedActionCountByProject);
    }

    private Map<Long, OrgQueryPort.MemberSummary> loadAskWhomMembers(
        Long departureMemberId,
        List<ActionReassignPort.HandoverableAction> handoverableActions,
        InsightSource source,
        OrgQueryPort orgQueryPort
    ) {
        Set<Long> memberIds = new LinkedHashSet<>();
        for (ActionReassignPort.HandoverableAction action : handoverableActions) {
            memberIds.addAll(originatorIds(departureMemberId, action.projectId(), source));
            memberIds.addAll(executorIds(departureMemberId, action.projectId(), source));
        }
        memberIds.remove(departureMemberId);
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return orgQueryPort.findMembers(List.copyOf(memberIds)).stream()
            .collect(Collectors.toMap(OrgQueryPort.MemberSummary::memberId, Function.identity(), (left, right) -> left));
    }

    private List<OwnershipProjectPayload> ownershipPayload(InsightSource source) {
        List<RankedOwnershipProject> payloads = new ArrayList<>();
        for (Map.Entry<Long, List<MeetingQueryPort.ProjectMeeting>> entry : source.meetingsByProject().entrySet()) {
            Long projectId = entry.getKey();
            List<MeetingQueryPort.ProjectMeeting> meetings = entry.getValue();
            long hostedMeetingCount = meetings.stream()
                .filter(meeting -> Objects.equals(meeting.hostMemberId(), source.departureMemberId()))
                .count();
            long attendedMeetingCount = meetings.stream()
                .filter(meeting -> source.attendeeIds(meeting.meetingId()).contains(source.departureMemberId()))
                .count();
            long assignedActionCount = source.assignedActionCountByProject().getOrDefault(projectId, 0L);
            int orderingWeight = (int) (hostedMeetingCount * 3 + attendedMeetingCount + assignedActionCount * 2);
            payloads.add(new RankedOwnershipProject(projectId, hostedMeetingCount, attendedMeetingCount, assignedActionCount, orderingWeight));
        }
        return payloads.stream()
            .sorted(Comparator.comparing(RankedOwnershipProject::orderingWeight).reversed()
                .thenComparing(RankedOwnershipProject::projectId))
            .map(payload -> new OwnershipProjectPayload(
                payload.projectId(),
                payload.hostedMeetingCount(),
                payload.attendedMeetingCount(),
                payload.assignedActionCount()
            ))
            .toList();
    }

    private List<OrphanAlertPayload> orphanPayload(
        Long departureMemberId,
        List<ActionReassignPort.TeamActionForDeparture> teamActions,
        InsightSource source
    ) {
        List<OrphanAlertPayload> payloads = new ArrayList<>();
        for (ActionReassignPort.TeamActionForDeparture action : teamActions) {
            if ("DONE".equalsIgnoreCase(action.status())) {
                continue;
            }
            MeetingQueryPort.ProjectMeeting sourceMeeting = source.findMeeting(action.sourceMeetingId());
            if (sourceMeeting == null) {
                continue;
            }
            boolean hosted = Objects.equals(sourceMeeting.hostMemberId(), departureMemberId);
            boolean attended = source.attendeeIds(sourceMeeting.meetingId()).contains(departureMemberId);
            if (!hosted && !attended) {
                continue;
            }
            payloads.add(new OrphanAlertPayload(
                action.actionId(),
                action.title(),
                action.projectId(),
                action.sourceMeetingId(),
                action.status(),
                action.teamId(),
                hosted ? "source_meeting_hosted" : "source_meeting_attended",
                "successor_assignment_recommended"
            ));
        }
        return payloads;
    }

    private AskWhomPayload askWhomPayload(
        Long departureMemberId,
        ActionReassignPort.HandoverableAction action,
        InsightSource source,
        Map<Long, OrgQueryPort.MemberSummary> members
    ) {
        List<MemberPayload> originators = originatorIds(departureMemberId, action.projectId(), source).stream()
            .map(members::get)
            .filter(Objects::nonNull)
            .map(MemberPayload::from)
            .toList();
        List<MemberPayload> executors = executorIds(departureMemberId, action.projectId(), source).stream()
            .map(members::get)
            .filter(Objects::nonNull)
            .map(MemberPayload::from)
            .toList();
        return new AskWhomPayload(action.actionId(), action.projectId(), originators, executors);
    }

    private List<Long> originatorIds(Long departureMemberId, Long projectId, InsightSource source) {
        return source.meetings(projectId).stream()
            .findFirst()
            .map(meeting -> limitedCoAttendees(departureMemberId, source.attendeeIds(meeting.meetingId())))
            .orElseGet(List::of);
    }

    private List<Long> executorIds(Long departureMemberId, Long projectId, InsightSource source) {
        return source.meetings(projectId).stream()
            .filter(meeting -> source.attendeeIds(meeting.meetingId()).contains(departureMemberId))
            .max(Comparator.comparing(
                MeetingQueryPort.ProjectMeeting::startAt, Comparator.nullsFirst(LocalDateTime::compareTo)))
            .map(meeting -> limitedCoAttendees(departureMemberId, source.attendeeIds(meeting.meetingId())))
            .orElseGet(List::of);
    }

    private List<Long> limitedCoAttendees(Long departureMemberId, List<Long> attendeeIds) {
        return attendeeIds.stream()
            .filter(memberId -> !Objects.equals(memberId, departureMemberId))
            .distinct()
            .limit(MAX_PEOPLE_PER_ROLE)
            .toList();
    }

    private ContextTimelinePayload contextTimelinePayload(
        ActionReassignPort.HandoverableAction action,
        InsightSource source
    ) {
        List<MeetingTimelinePayload> meetings = source.meetings(action.projectId()).stream()
            .map(meeting -> new MeetingTimelinePayload(
                meeting.meetingId(),
                meeting.title(),
                meeting.startAt(),
                Objects.equals(meeting.meetingId(), action.sourceMeetingId()),
                source.topics(meeting.meetingId()).stream()
                    .map(topic -> new TopicPayload(topic.topicId(), topic.parentTopicId(), topic.topicType(), topic.content(), topic.sortOrder()))
                    .toList()
            ))
            .toList();
        return new ContextTimelinePayload(action.actionId(), action.projectId(), action.sourceMeetingId(), meetings);
    }

    private HandoverInsight newInsight(
        Long handoverId,
        Long actionId,
        HandoverInsightKind kind,
        Object payload,
        int sortOrder
    ) {
        try {
            return HandoverInsight.newSnapshot(handoverId, actionId, kind, OBJECT_MAPPER.writeValueAsString(payload), sortOrder);
        } catch (JsonProcessingException e) {
            // 어떤 kind/actionId payload 직렬화가 실패했는지 원인 예외로 보존해 로그에서 추적 가능하게 한다.
            throw new BusinessException(HandoverErrorCode.HO_CONFLICT, e);
        }
    }

    private record InsightSource(
        Long departureMemberId,
        Map<Long, List<MeetingQueryPort.ProjectMeeting>> meetingsByProject,
        Map<Long, List<MeetingQueryPort.MeetingAttendee>> attendeesByMeeting,
        Map<Long, List<MeetingQueryPort.MeetingTopic>> topicsByMeeting,
        Map<Long, Long> assignedActionCountByProject
    ) {

        List<MeetingQueryPort.ProjectMeeting> meetings(Long projectId) {
            if (projectId == null) {
                return List.of();
            }
            return meetingsByProject.getOrDefault(projectId, List.of());
        }

        List<Long> attendeeIds(Long meetingId) {
            if (meetingId == null) {
                return List.of();
            }
            return attendeesByMeeting.getOrDefault(meetingId, List.of()).stream()
                .map(MeetingQueryPort.MeetingAttendee::memberId)
                .toList();
        }

        List<MeetingQueryPort.MeetingTopic> topics(Long meetingId) {
            if (meetingId == null) {
                return List.of();
            }
            return topicsByMeeting.getOrDefault(meetingId, List.of());
        }

        MeetingQueryPort.ProjectMeeting findMeeting(Long meetingId) {
            if (meetingId == null) {
                return null;
            }
            return meetingsByProject.values().stream()
                .flatMap(List::stream)
                .filter(meeting -> Objects.equals(meeting.meetingId(), meetingId))
                .findFirst()
                .orElse(null);
        }
    }

    private record OwnershipProjectPayload(
        Long projectId,
        long hostedMeetingCount,
        long attendedMeetingCount,
        long assignedActionCount
    ) {
    }

    private record RankedOwnershipProject(
        Long projectId,
        long hostedMeetingCount,
        long attendedMeetingCount,
        long assignedActionCount,
        int orderingWeight
    ) {
    }

    private record OrphanAlertPayload(
        Long actionId,
        String title,
        Long projectId,
        Long sourceMeetingId,
        String status,
        Long teamId,
        String basis,
        String recommendation
    ) {
    }

    private record AskWhomPayload(
        Long actionId,
        Long projectId,
        List<MemberPayload> originators,
        List<MemberPayload> executors
    ) {
    }

    private record MemberPayload(
        Long memberId,
        String name,
        String position
    ) {

        static MemberPayload from(OrgQueryPort.MemberSummary member) {
            return new MemberPayload(member.memberId(), member.name(), member.position());
        }
    }

    private record ContextTimelinePayload(
        Long actionId,
        Long projectId,
        Long sourceMeetingId,
        List<MeetingTimelinePayload> meetings
    ) {
    }

    private record MeetingTimelinePayload(
        Long meetingId,
        String title,
        LocalDateTime startAt,
        boolean sourceMeeting,
        List<TopicPayload> topics
    ) {
    }

    private record TopicPayload(
        Long topicId,
        Long parentTopicId,
        String topicType,
        String content,
        int sortOrder
    ) {
    }
}
