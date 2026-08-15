package com.module06.backend.capture.infrastructure.stt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.infrastructure.stt.SttTranscribeResultAdapter.S3Location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STT 결과 위치 해석 — Transcribe 가 알려준 URI 를 S3 오브젝트 키로 되돌린다.
 *
 * <p><b>여기가 틀리면 권한 문제로 위장된다.</b> 없는 키를 조회하면 S3 는 404 를 주지만, 롤에
 * {@code s3:ListBucket} 이 없으면 존재 여부를 감추려고 <b>403 AccessDenied</b> 로 바꿔 돌려준다.
 * 그래서 로그에는 "ListBucket 권한 없음"이 찍히고 IAM 을 뒤지게 되는데, 실제 원인은 키가
 * 어긋난 것이다 — 2026-08-15 운영에서 정확히 그렇게 반나절을 썼다.
 *
 * <p>그리고 <b>파일명이 깨끗하면 통과한다.</b> 인코딩 전후가 같기 때문이다. 그래서 이 버그는
 * 사용자가 공백·괄호·한글이 든 파일을 올릴 때까지 숨어 있는다.
 */
class SttTranscribeResultLocationTest {

    private static final String HOST = "https://s3.ap-northeast-2.amazonaws.com";
    private static final String BUCKET = "zebra-storage-prod";

    @Test
    @DisplayName("퍼센트 인코딩된 파일명을 원문 키로 되돌린다 — 공백·괄호")
    void 인코딩된_파일명을_되돌린다() {
        // 2026-08-15 운영에서 실제로 온 URI 다. 원본 파일명이 "videoplayback (1).m4a" 였다.
        S3Location location = S3Location.parse(
                HOST + "/" + BUCKET + "/stt-out/recordings/org-11/videoplayback%20%281%29.m4a.json");

        assertThat(location.bucket()).isEqualTo(BUCKET);
        assertThat(location.key())
                .isEqualTo("stt-out/recordings/org-11/videoplayback (1).m4a.json");
    }

    @Test
    @DisplayName("한글 파일명도 되돌린다 — 같은 종류로 두 번 터졌다(#514 · #516)")
    void 한글_파일명을_되돌린다() {
        S3Location location = S3Location.parse(
                HOST + "/" + BUCKET + "/stt-out/recordings/org-17/%ED%9A%8C%EC%9D%98%20%EB%85%B9%EC%9D%8C.m4a.json");

        assertThat(location.key()).isEqualTo("stt-out/recordings/org-17/회의 녹음.m4a.json");
    }

    @Test
    @DisplayName("깨끗한 파일명은 그대로다 — 이 경로가 되니까 버그가 오래 숨어 있었다")
    void 평범한_파일명은_그대로다() {
        S3Location location = S3Location.parse(
                HOST + "/" + BUCKET + "/stt-out/recordings/org-17/meeting-gold-001.json");

        assertThat(location.key()).isEqualTo("stt-out/recordings/org-17/meeting-gold-001.json");
    }

    @Test
    @DisplayName("키의 + 는 공백으로 바꾸지 않는다 — URLDecoder 를 쓰면 여기서 망가진다")
    void 플러스는_공백이_되지_않는다() {
        /*
         * URLDecoder 는 폼 인코딩 규칙이라 '+' 를 공백으로 바꾼다. S3 키의 '+' 는 그냥 '+' 이고,
         * 바꿔버리면 또 없는 키를 조회하게 된다 — 고치려던 버그를 다른 모양으로 다시 만든다.
         */
        S3Location location = S3Location.parse(HOST + "/" + BUCKET + "/stt-out/a+b/c.json");

        assertThat(location.key()).isEqualTo("stt-out/a+b/c.json");
    }

    @Test
    @DisplayName("presigned 형태로 오면 쿼리스트링을 잘라낸다 — 서명이 키에 섞이면 안 된다")
    void 쿼리스트링을_잘라낸다() {
        S3Location location = S3Location.parse(
                HOST + "/" + BUCKET + "/stt-out/a.json?X-Amz-Signature=deadbeef&X-Amz-Expires=900");

        assertThat(location.key()).isEqualTo("stt-out/a.json");
    }

    @Test
    @DisplayName("s3:// 는 디코딩하지 않는다 — 그 표현의 경로는 이미 원문 키다")
    void s3_스킴은_그대로_읽는다() {
        S3Location location = S3Location.parse("s3://" + BUCKET + "/stt-out/videoplayback (1).m4a.json");

        assertThat(location.bucket()).isEqualTo(BUCKET);
        assertThat(location.key()).isEqualTo("stt-out/videoplayback (1).m4a.json");
    }

    @Test
    @DisplayName("해석할 수 없는 URI 는 던진다 — 조용히 엉뚱한 키를 만들지 않는다")
    void 해석_불가는_던진다() {
        // 스킴이 없다.
        assertThatThrownBy(() -> S3Location.parse("zebra-storage-prod/stt-out/a.json"))
                .isInstanceOf(IllegalArgumentException.class);

        // 버킷만 있고 키가 없다.
        assertThatThrownBy(() -> S3Location.parse(HOST + "/" + BUCKET))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
