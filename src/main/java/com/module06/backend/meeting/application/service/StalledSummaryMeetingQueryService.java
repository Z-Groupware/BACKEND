package com.module06.backend.meeting.application.service;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.port.out.SummaryStatusQueryPort;
import com.module06.backend.meeting.application.port.out.SummaryStatusQueryPort.StalledSummaryMeeting;
import com.module06.backend.meeting.application.query.GetStalledSummaryMeetingsQuery;
import com.module06.backend.meeting.application.result.StalledSummaryMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetStalledSummaryMeetingsUseCase;
import com.module06.backend.meeting.domain.repository.StalledSummaryMeetingRepository;
import com.module06.backend.meeting.domain.repository.StalledSummaryMeetingRepository.StalledSummaryMeetingCandidate;

/*
 * MEET-15 요약 중단·실패 회의 목록 조회를 조율하는 애플리케이션 서비스다.
 *
 * D가 회사·host·종료 회의 후보를 정하고 A가 요약 중단·실패를 배치 판정한 뒤 교집합만 반환한다.
 */
@Service
@RequiredArgsConstructor
public class StalledSummaryMeetingQueryService implements GetStalledSummaryMeetingsUseCase {

    /* 페이지 번호가 생략됐을 때 적용하는 첫 페이지 번호다. */
    private static final int DEFAULT_PAGE = 0;

    /* 페이지 크기가 생략됐을 때 적용하는 기본 회의 개수다. */
    private static final int DEFAULT_SIZE = 20;

    /* 한 페이지에서 허용하는 최대 회의 개수다. */
    private static final int MAX_SIZE = 100;

    /* 회사·host·종료 상태로 D 소유 후보 회의를 조회하는 저장소다. */
    private final StalledSummaryMeetingRepository stalledSummaryMeetingRepository;

    /* 후보 중 요약 중단·실패 회의를 배치 판정하는 A 연동 Port다. */
    private final SummaryStatusQueryPort summaryStatusQueryPort;

    /* 로그인 사용자가 개설한 종료 회의 중 요약에 문제가 생긴 회의를 필터·페이징한다. */
    @Override
    @Transactional(readOnly = true)
    public StalledSummaryMeetingListResult getStalledSummaryMeetings(GetStalledSummaryMeetingsQuery query) {
        /* 인증 식별자와 필터 및 페이지 값을 검증해 내부 조회 조건을 확정한다. */
        ResolvedQuery resolved = validateAndResolve(query);

        /* 회사·host·DONE 조건을 데이터베이스에서 적용한 최근 회의 후보를 조회한다. */
        List<StalledSummaryMeetingCandidate> candidates = stalledSummaryMeetingRepository
                .findHostedDoneSummaryCandidates(resolved.companyId(), resolved.requesterMemberId())
                .stream()
                .filter(candidate -> matchesFilters(candidate, resolved))
                .toList();

        /* 후보가 없으면 A Port를 호출하지 않고 요청 페이지 메타와 빈 목록을 반환한다. */
        if (candidates.isEmpty()) {
            return emptyResult(resolved);
        }

        /* 필터를 통과한 후보 전체를 한 번에 넘겨 A가 인정한 문제 회의와 상태를 조회한다. */
        Map<Long, Boolean> stalledStatuses = indexStatuses(summaryStatusQueryPort.findStalledSummaries(
                resolved.companyId(),
                candidates.stream().map(StalledSummaryMeetingCandidate::meetingId).toList()
        ));

        /* D 후보 순서를 유지하면서 A가 반환한 회의만 남기고 화면 카드 결과로 변환한다. */
        List<StalledSummaryMeetingListResult.MeetingItem> matched = candidates.stream()
                .filter(candidate -> stalledStatuses.containsKey(candidate.meetingId()))
                .map(candidate -> new StalledSummaryMeetingListResult.MeetingItem(
                        candidate.meetingId(),
                        candidate.title(),
                        stalledStatuses.get(candidate.meetingId())
                ))
                .toList();

        /* 전체 문제 회의 수를 기준으로 페이지 메타와 현재 페이지 내용을 계산한다. */
        return paginate(matched, resolved);
    }

    /* 인증·필터·페이지 값을 검증하고 기본값이 채워진 내부 Query를 반환한다. */
    private ResolvedQuery validateAndResolve(GetStalledSummaryMeetingsQuery query) {
        /* 회사와 로그인 구성원을 식별할 수 없으면 저장소 및 A Port 호출 전에 거절한다. */
        if (query == null
                || query.companyId() == null
                || query.companyId() <= 0L
                || query.requesterMemberId() == null
                || query.requesterMemberId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 프로젝트 필터는 생략하거나 양수 식별자만 사용할 수 있다. */
        if (query.projectId() != null && query.projectId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 내부 호출에서 페이지 값이 생략돼도 REST 기본값과 동일하게 처리한다. */
        int page = query.page() == null ? DEFAULT_PAGE : query.page();
        int size = query.size() == null ? DEFAULT_SIZE : query.size();

        /* 음수 페이지나 1~100 범위를 벗어난 페이지 크기는 공통 입력 오류로 거절한다. */
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 두 날짜가 모두 있을 때 시작일이 종료일보다 늦으면 성립하지 않는 범위다. */
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 검증된 인증·필터·페이지 값을 외부 호출에서 바꿀 수 없는 내부 값으로 묶는다. */
        return new ResolvedQuery(
                query.companyId(),
                query.requesterMemberId(),
                query.projectId(),
                query.from(),
                query.to(),
                page,
                size
        );
    }

    /* D 후보 회의가 요청 프로젝트와 시작일 범위에 포함되는지 판단한다. */
    private boolean matchesFilters(StalledSummaryMeetingCandidate candidate, ResolvedQuery query) {
        /* 프로젝트가 지정된 경우 같은 프로젝트 회의만 남긴다. */
        if (query.projectId() != null && !query.projectId().equals(candidate.projectId())) {
            return false;
        }

        /* 시작일 하한보다 앞선 회의는 목록에서 제외한다. */
        if (query.from() != null && candidate.startAt().isBefore(query.from().atStartOfDay())) {
            return false;
        }

        /* 종료일의 마지막 시각보다 뒤인 회의는 목록에서 제외한다. */
        return query.to() == null || !candidate.startAt().isAfter(query.to().atTime(LocalTime.MAX));
    }

    /* A의 문제 회의 목록을 식별자별 중단 여부 맵으로 변환한다. */
    private Map<Long, Boolean> indexStatuses(List<StalledSummaryMeeting> summaries) {
        /* A가 요청하지 않은 식별자를 반환해도 이후 D 후보와 교집합만 사용하도록 맵을 만든다. */
        Map<Long, Boolean> indexed = new LinkedHashMap<>();
        for (StalledSummaryMeeting summary : summaries) {
            /* null 식별자는 정상 판정으로 사용할 수 없으므로 응답 조립 대상에서 제외한다. */
            if (summary.meetingId() != null) {
                indexed.put(summary.meetingId(), summary.stalled());
            }
        }

        /* 응답 조립 중 상태가 바뀌지 않도록 불변 맵으로 반환한다. */
        return Map.copyOf(indexed);
    }

    /* 전체 교집합 결과에서 요청 페이지 한 구간과 페이지 메타를 만든다. */
    private StalledSummaryMeetingListResult paginate(
            List<StalledSummaryMeetingListResult.MeetingItem> meetings,
            ResolvedQuery query
    ) {
        /* 전체 결과가 비어도 요청 페이지와 크기를 유지한 정상 빈 목록을 반환한다. */
        if (meetings.isEmpty()) {
            return emptyResult(query);
        }

        /* long 곱셈으로 페이지 시작 위치의 int 오버플로를 피하고 목록 범위를 확인한다. */
        long requestedFromIndex = (long) query.page() * query.size();
        int totalPages = (int) ((meetings.size() + (long) query.size() - 1L) / query.size());

        /* 전체 범위를 벗어난 페이지는 빈 내용과 실제 전체 건수·페이지 수를 반환한다. */
        if (requestedFromIndex >= meetings.size()) {
            return new StalledSummaryMeetingListResult(
                    List.of(),
                    new StalledSummaryMeetingListResult.Page(
                            query.page(), query.size(), meetings.size(), totalPages
                    )
            );
        }

        /* 시작과 끝 인덱스를 안전하게 계산해 요청 페이지의 불변 부분 목록을 만든다. */
        int fromIndex = (int) requestedFromIndex;
        int toIndex = Math.min(fromIndex + query.size(), meetings.size());

        /* 현재 페이지 내용과 전체 문제 회의 기준 메타데이터를 함께 반환한다. */
        return new StalledSummaryMeetingListResult(
                meetings.subList(fromIndex, toIndex),
                new StalledSummaryMeetingListResult.Page(
                        query.page(), query.size(), meetings.size(), totalPages
                )
        );
    }

    /* 외부 Port 호출 없이 반환할 정상 빈 목록과 페이지 메타를 만든다. */
    private StalledSummaryMeetingListResult emptyResult(ResolvedQuery query) {
        /* 빈 목록도 프론트가 null 분기 없이 렌더링할 수 있도록 명시적인 배열로 반환한다. */
        return new StalledSummaryMeetingListResult(
                List.of(),
                new StalledSummaryMeetingListResult.Page(query.page(), query.size(), 0L, 0)
        );
    }

    /* 검증을 마친 인증·필터·페이지 값을 서비스 내부에서만 사용하는 불변 값으로 묶는다. */
    private record ResolvedQuery(
            Long companyId,
            Long requesterMemberId,
            Long projectId,
            java.time.LocalDate from,
            java.time.LocalDate to,
            int page,
            int size
    ) {
    }
}
