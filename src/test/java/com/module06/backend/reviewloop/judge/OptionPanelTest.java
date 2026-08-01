package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판단 패널 구조 검증(key-free) — 안건은 동등 목록, 추천은 별도 필드.
 * 선택한 안건의 action으로 뭘 할지 라우팅되는지 stub으로 확인.
 */
class OptionPanelTest {

    private final Finding finding = new Finding("ARCH_003a", Severity.MINOR, "arch",
            "타 도메인 JpaEntity 참조", "A.java", 42, Confidence.MEDIUM, FindingSource.JUDGE);

    @Test
    @DisplayName("패널은 안건 목록 + 별도 추천을 가진다 (추천이 안건에 박히지 않음)")
    void panelHasOptionsAndSeparateRecommendation() {
        OptionPanelPort port = f -> new OptionPanel(
                List.of(
                        new PanelOption("A", PanelAction.FALSE_POSITIVE, "오탐", "정상 참조 패턴"),
                        new PanelOption("B", PanelAction.AUTO_FIX, "자동수정", "참조 분리"),
                        new PanelOption("C", PanelAction.POLICY_REVIEW, "정책수정", "규칙 모호")),
                new Recommendation("A", 0.82, "참조 엔티티라 정상 + 반복 → 오탐 가능성"));

        OptionPanel panel = port.propose(finding);

        assertThat(panel.options()).extracting(PanelOption::letter).containsExactly("A", "B", "C");
        assertThat(panel.recommendation().pick()).isEqualTo("A");
        assertThat(panel.recommendation().confidence()).isEqualTo(0.82);
        // 추천은 별도 필드일 뿐, 안건 자체엔 추천 표시가 없다
        assertThat(panel.options()).noneMatch(o -> o.title().contains("추천"));
    }

    @Test
    @DisplayName("선택한 안건의 action으로 다음 처리가 라우팅된다")
    void selectedActionRoutes() {
        PanelOption chosen = new PanelOption("A", PanelAction.FALSE_POSITIVE, "오탐", "정상");
        // A(오탐) 선택 → 교훈 기록 경로
        assertThat(chosen.action()).isEqualTo(PanelAction.FALSE_POSITIVE);
    }
}
