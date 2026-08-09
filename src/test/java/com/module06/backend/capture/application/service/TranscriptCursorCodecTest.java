package com.module06.backend.capture.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.module06.backend.capture.domain.model.TranscriptCursor;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ANLZ-05 커서 코덱.
 *
 * <p>커서는 우리가 발행해 클라이언트가 그대로 되돌려주는 값이다. 왕복에서 값이 변하면 페이지
 * 경계가 어긋나 발화가 빠지거나 겹쳐 나가는데, 그건 응답만 봐서는 드러나지 않는다.
 */
class TranscriptCursorCodecTest {

    private final TranscriptCursorCodec codec = new TranscriptCursorCodec();

    @Test
    @DisplayName("실어 보낸 커서를 그대로 되돌려 받는다")
    void 왕복해도_값이_같다() {
        TranscriptCursor cursor = new TranscriptCursor(623400, 372);

        assertThat(codec.decode(codec.encode(cursor))).isEqualTo(cursor);
    }

    @Test
    @DisplayName("오프셋 없는 꼬리 구간 커서도 왕복한다 — null 이 구간 표시라 잃으면 안 된다")
    void 오프셋이_null_인_커서도_왕복한다() {
        TranscriptCursor tail = new TranscriptCursor(null, 998);

        TranscriptCursor decoded = codec.decode(codec.encode(tail));

        assertThat(decoded).isEqualTo(tail);
        // null 이 0 으로 바뀌면 꼬리 구간이 본문 맨 앞으로 읽혀 이미 보낸 발화를 다시 보낸다.
        assertThat(decoded.isInNullOffsetTail()).isTrue();
    }

    @Test
    @DisplayName("커서가 없으면 첫 페이지다")
    void 빈_커서는_null_이다() {
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode("   ")).isNull();
    }

    @Test
    @DisplayName("해석할 수 없는 커서는 400 이다 — 조용히 첫 페이지로 되돌리지 않는다")
    void 깨진_커서는_400_이다() {
        // 첫 페이지로 되돌리면 화면이 맨 앞부터 다시 그리고, 사용자에게는 목록이
        // 무한히 반복되는 것으로만 보인다. 원인을 찾을 단서가 응답에 남아야 한다.
        assertThatThrownBy(() -> codec.decode("!!!not-base64!!!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .satisfies(code -> {
                    assertThat(code).isEqualTo(CaptureErrorCode.TRANSCRIPT_CURSOR_INVALID);
                    assertThat(code.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("base64 는 맞지만 우리 커서가 아니면 400 이다")
    void 다른_형식의_커서는_400_이다() {
        String notOurCursor = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"page\":3}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // seq 가 없으면 페이지 경계를 잡을 수 없다. 다른 API 의 커서를 넣은 경우다.
        assertThatThrownBy(() -> codec.decode(notOurCursor))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CaptureErrorCode.TRANSCRIPT_CURSOR_INVALID);
    }
}
