package com.module06.backend.search.application.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.search.application.query.RecentQueryCommand;
import com.module06.backend.search.application.query.RecentViewCommand;
import com.module06.backend.search.application.query.SearchQuery;
import com.module06.backend.search.application.result.SearchOverviewResult;
import com.module06.backend.search.application.result.SearchResult;
import com.module06.backend.search.application.usecase.SearchUseCase;
import com.module06.backend.search.domain.exception.SearchErrorCode;
import com.module06.backend.search.domain.model.SearchType;
import com.module06.backend.search.domain.repository.SearchQueryRepository;
import com.module06.backend.search.domain.repository.SearchQueryRepository.SearchHit;
import com.module06.backend.search.domain.repository.SearchQueryRepository.SearchScope;

/*
 * 통합 검색 조회를 조율하는 읽기 전용 유스케이스다.
 *
 * 권한 스코프 자체는 저장소 Query WHERE 절에서 강제하고, 서비스는 입력 검증과 타입별 결과 병합만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class SearchService implements SearchUseCase {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int RECENT_LIMIT = 10;

    private final SearchQueryRepository searchQueryRepository;

    @Override
    @Transactional(readOnly = true)
    public SearchResult search(SearchQuery query) {
        String keyword = normalizeKeyword(query);
        int limit = resolveLimit(query.limit());
        SearchType requestedType = query.type() == null ? SearchType.ALL : query.type();

        /*
         * SR-1에서는 tags/from/to를 API 시그니처에만 유지한다.
         * SR-2에서 프로젝트 태그 및 기간 조건을 저장소 WHERE 절에 연결한다.
         */
        SearchScope scope = new SearchScope(
                query.companyId(),
                query.requesterMemberId(),
                query.requesterTeamId(),
                query.requesterRole(),
                query.admin(),
                query.tags() == null ? List.of() : List.copyOf(query.tags()),
                query.from(),
                query.to()
        );

        long meetingCount = countIfRequested(scope, SearchType.MEETING, requestedType, keyword);
        long actionCount = countIfRequested(scope, SearchType.ACTION, requestedType, keyword);
        long projectCount = countIfRequested(scope, SearchType.PROJECT, requestedType, keyword);
        long personCount = countIfRequested(scope, SearchType.PERSON, requestedType, keyword);

        List<SearchResult.Item> results = requestedTypes(requestedType).stream()
                .flatMap(type -> searchQueryRepository.search(scope, type, keyword, limit).stream())
                .map(this::toResultItem)
                .sorted(Comparator
                        .comparingDouble(SearchResult.Item::score).reversed()
                        .thenComparing(SearchResult.Item::date, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SearchResult.Item::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();

        return new SearchResult(
                keyword,
                new SearchResult.Counts(
                        meetingCount + actionCount + projectCount + personCount,
                        meetingCount,
                        actionCount,
                        projectCount,
                        personCount
                ),
                results
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SearchOverviewResult overview(SearchScope scope) {
        validateScope(scope.companyId(), scope.requesterMemberId());
        SearchScope normalizedScope = new SearchScope(
                scope.companyId(),
                scope.requesterMemberId(),
                scope.requesterTeamId(),
                scope.requesterRole(),
                scope.admin(),
                List.of(),
                null,
                null
        );

        return new SearchOverviewResult(
                searchQueryRepository.findRecentQueries(normalizedScope, RECENT_LIMIT),
                searchQueryRepository.findRecentItems(normalizedScope, RECENT_LIMIT),
                searchQueryRepository.findOverviewProjects(normalizedScope),
                searchQueryRepository.findOverviewPeople(normalizedScope)
        );
    }

    @Override
    @Transactional
    public void saveRecentQuery(RecentQueryCommand command) {
        if (command == null) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER);
        }
        validateScope(command.companyId(), command.memberId());
        String query = command.query() == null ? "" : command.query().trim();
        if (query.isBlank()) {
            throw new BusinessException(SearchErrorCode.BLANK_QUERY);
        }
        searchQueryRepository.saveRecentQuery(command.companyId(), command.memberId(), query);
        searchQueryRepository.pruneRecentQueries(command.companyId(), command.memberId(), RECENT_LIMIT);
    }

    @Override
    @Transactional
    public void saveRecentView(RecentViewCommand command) {
        if (command == null) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER);
        }
        validateScope(command.companyId(), command.memberId());
        if (command.type() == null
                || command.type() == SearchType.ALL
                || command.id() == null
                || command.id() <= 0L) {
            throw new BusinessException(SearchErrorCode.INVALID_RECENT_VIEW_TYPE);
        }
        searchQueryRepository.saveRecentView(command.companyId(), command.memberId(), command.type(), command.id());
        searchQueryRepository.pruneRecentViews(command.companyId(), command.memberId(), RECENT_LIMIT);
    }

    private long countIfRequested(SearchScope scope, SearchType countedType, SearchType requestedType, String keyword) {
        if (requestedType != SearchType.ALL && requestedType != countedType) {
            return 0L;
        }
        return searchQueryRepository.count(scope, countedType, keyword);
    }

    private List<SearchType> requestedTypes(SearchType type) {
        if (type == null || type == SearchType.ALL) {
            return List.of(SearchType.MEETING, SearchType.ACTION, SearchType.PROJECT, SearchType.PERSON);
        }
        return List.of(type);
    }

    private SearchResult.Item toResultItem(SearchHit hit) {
        return new SearchResult.Item(
                hit.type(),
                hit.id(),
                hit.title(),
                hit.snippet(),
                hit.project() == null ? null : new SearchResult.Project(
                        hit.project().id(),
                        hit.project().tag(),
                        hit.project().name(),
                        hit.project().color()
                ),
                hit.date(),
                hit.role(),
                hit.score()
        );
    }

    private String normalizeKeyword(SearchQuery query) {
        if (query == null
                || !isValidId(query.companyId())
                || !isValidId(query.requesterMemberId())) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER);
        }
        String keyword = query.keyword() == null ? "" : query.keyword().trim();
        if (keyword.isBlank()) {
            throw new BusinessException(SearchErrorCode.BLANK_QUERY);
        }
        return keyword;
    }

    private int resolveLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_LIMIT : limit;
        if (resolved < 1 || resolved > MAX_LIMIT) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER);
        }
        return resolved;
    }

    private void validateScope(Long companyId, Long memberId) {
        if (!isValidId(companyId) || !isValidId(memberId)) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER);
        }
    }

    private boolean isValidId(Long id) {
        return id != null && id > 0L;
    }
}
