package com.module06.backend.search.application.usecase;

import com.module06.backend.search.application.query.SearchQuery;
import com.module06.backend.search.application.result.SearchResult;

public interface SearchUseCase {

    SearchResult search(SearchQuery query);
}
