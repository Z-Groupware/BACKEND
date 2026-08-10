package com.module06.backend.project.domain.model;

/* comment.
    프로젝트 색상 — FE 고정 팔레트 11개 중 하나만 허용(2026-08-10, 다크모드 대응 위해
    자유 HEX 금지 확정). 저장 자체는 여전히 HEX 문자열이고 팔레트 키로 바꾸지 않는다 —
    검증만 이 11개로 좁힌다(이홍근 요청, allow-list 방식).
*/
public final class ProjectColorPalette {

    public static final String REGEXP =
            "^#(475569|A16207|4D7C0F|059669|0D9488|0891B2|0284C7|4F46E5|9333EA|C026D3|DB2777)$";

    private ProjectColorPalette() {
    }
}
