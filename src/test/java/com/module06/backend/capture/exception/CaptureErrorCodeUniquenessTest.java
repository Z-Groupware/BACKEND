package com.module06.backend.capture.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 에러 코드 문자열은 이 enum 안에서 유일해야 한다.
 *
 * <p>이 테스트가 있는 이유 — <b>실제로 중복이 났고 테스트가 아니라 리뷰가 잡았다.</b>
 * {@code RESUME_NOTHING_TO_RESUME} 을 넣을 때 {@code ANLZ-005} 를 골랐는데 그 번호는 이미
 * {@code SUMMARY_ITEM_NOT_FOUND}(404) 가 쓰고 있었다(CodeRabbit PR #318).
 *
 * <p>중복이 위험한 이유는 컴파일이 되기 때문이다. 프론트는 코드 문자열로 문구를 고르므로,
 * 같은 코드가 409(재개할 계층 없음)와 404(항목 없음) 둘을 뜻하면 <b>어느 쪽 안내를 띄울지
 * 정할 수 없다.</b> 상태 코드가 다르니 화면은 "둘 중 하나"를 고르게 되고, 그 선택은 반드시
 * 한쪽에서 틀린다.
 *
 * <p>⚠ 이 테스트는 capture 도메인만 본다. 프로젝트 전체(도메인 간)에도 같은 충돌이 있다 —
 * {@code MT-001}~{@code MT-005} 가 meeting 과 metering 양쪽에서 서로 다른 뜻으로 쓰인다.
 * 그건 두 도메인 담당자의 합의가 필요해 별건으로 둔다.
 */
class CaptureErrorCodeUniquenessTest {

    @Test
    @DisplayName("코드 문자열이 중복되지 않는다 — 같은 코드가 두 뜻이면 화면이 문구를 고를 수 없다")
    void 코드가_중복되지_않는다() {
        Map<String, List<String>> namesByCode = new LinkedHashMap<>();
        for (CaptureErrorCode code : CaptureErrorCode.values()) {
            namesByCode.computeIfAbsent(code.getCode(), key -> new ArrayList<>()).add(code.name());
        }

        Map<String, List<String>> duplicated = new LinkedHashMap<>();
        namesByCode.forEach((code, names) -> {
            if (names.size() > 1) {
                duplicated.put(code, names);
            }
        });

        assertThat(duplicated)
                .withFailMessage("중복된 에러 코드가 있습니다 — %s. "
                        + "새 코드는 빈 번호를 잡으세요. 이미 쓰이는 코드를 옮기면 그 코드로 "
                        + "분기하는 프론트가 조용히 깨집니다.", duplicated)
                .isEmpty();
    }

    @Test
    @DisplayName("코드에 빈 값이 없다")
    void 코드가_비어_있지_않다() {
        for (CaptureErrorCode code : CaptureErrorCode.values()) {
            assertThat(code.getCode())
                    .withFailMessage("%s 의 코드가 비어 있습니다.", code.name())
                    .isNotBlank();
            assertThat(code.getMessage())
                    .withFailMessage("%s 의 메시지가 비어 있습니다.", code.name())
                    .isNotBlank();
        }
    }
}
