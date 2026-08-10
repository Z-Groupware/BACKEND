package com.module06.backend.identity.member.application.dto;

import java.util.List;

/**
 * 구성원 목록의 페이징 결과다. 전체 건수 이름은 표준(totalElements)을 따르고,
 * 마지막 페이지 판정에 필요한 totalPages·hasNext 를 함께 실어 보낸다 — FE 가
 * totalElements/size 로 다시 계산하지 않게 한다.
 */
public record MemberPage(
        long totalElements,
        int totalPages,
        boolean hasNext,
        int page,
        int size,
        List<MemberListItem> content
) {

    /** totalElements·page·size 로부터 totalPages 와 hasNext 를 파생해 조립한다. */
    public static MemberPage of(long totalElements, int page, int size, List<MemberListItem> content) {
        /* size 는 컨트롤러가 1 이상으로 막지만, 0 이 들어와도 0 나눗셈으로 터지지 않게 한다. */
        int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new MemberPage(totalElements, totalPages, page + 1 < totalPages, page, size, content);
    }
}
