package com.module06.backend.search.application.usecase;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.search.application.query.RecentQueryCommand;
import com.module06.backend.search.application.query.RecentViewCommand;
import com.module06.backend.search.application.query.SearchQuery;
import com.module06.backend.search.application.result.SearchOverviewResult;
import com.module06.backend.search.application.result.SearchResult;
import com.module06.backend.search.domain.exception.SearchErrorCode;
import com.module06.backend.search.domain.repository.SearchQueryRepository.SearchScope;

public interface SearchUseCase {

    SearchResult search(SearchQuery query);

    default SearchOverviewResult overview(SearchScope scope) {
        throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER);
    }

    default void saveRecentQuery(RecentQueryCommand command) {
        throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER);
    }

    default void saveRecentView(RecentViewCommand command) {
        throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER);
    }
}
