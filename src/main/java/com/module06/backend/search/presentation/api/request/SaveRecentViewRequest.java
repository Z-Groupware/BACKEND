package com.module06.backend.search.presentation.api.request;

public record SaveRecentViewRequest(
        String type,
        Long id
) {
}
