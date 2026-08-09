package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.port.out.TranscriptRepository.UtteranceView;
import com.module06.backend.capture.domain.model.TranscriptCursor;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * ANLZ-05 커서 페이징을 실제 DB 위에서 검증한다(이슈 #248).
 *
 * <h2>왜 가짜 저장소로는 부족한가</h2>
 * 이 구현의 주장은 **DB 가 무엇을 돌려주는가**에 대한 것이다 — 오프셋이 NULL 인 발화가 어디로
 * 가는지, 같은 오프셋이 여럿일 때 페이지 경계에서 무엇이 남는지. 가짜 저장소는 우리가 맞다고
 * 믿는 대로 동작하므로 그 주장을 검증하지 못한다.
 *
 * 특히 정렬의 NULL 위치는 DB 기본값이 갈리는 자리다(MySQL·H2 모두 오름차순에서 NULL 이 앞이다).
 * 어댑터가 구간을 나눠 뜨는 이유가 그것이고, 여기서 확인하는 것도 그것이다.
 *
 * ⚠ H2(MODE=MySQL) 다. 스키마는 Hibernate create-drop 이 소유하고 Flyway 는 꺼져 있다
 * (테스트 application.yaml). transcript_chunk 는 쓰기 경로가 화자 두 컬럼뿐이라 —
 * 정본 적재는 다른 도메인 소유다 — 픽스처는 SQL 로 직접 넣는다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:anlz05db;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DisplayName("ANLZ-05 정본 커서 페이징 — 빠짐도 겹침도 없다")
class TranscriptPagingPersistenceTest {

    private static final long MEETING = 500L;
    private static final long OTHER_MEETING = 501L;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM transcript_chunk");
    }

    @Test
    @DisplayName("오프셋 없는 발화는 맨 뒤로 간다 — 정렬 기본값에 맡기지 않는다")
    void 오프셋_없는_발화는_꼬리로_간다() {
        insert(1L, MEETING, 1, 300, "세 번째");
        insert(2L, MEETING, 2, 100, "첫 번째");
        insert(3L, MEETING, 3, null, "오프셋 없음");
        insert(4L, MEETING, 4, 200, "두 번째");

        List<UtteranceView> page = transcriptRepository.findPage(MEETING, null, 10);

        // NULL 을 정렬에 맡기면 H2·MySQL 모두 오름차순의 맨 앞으로 보낸다 —
        // 그러면 위치를 모르는 발화가 회의 앞머리에 붙는다.
        assertThat(page).extracting(UtteranceView::content)
                .containsExactly("첫 번째", "두 번째", "세 번째", "오프셋 없음");
    }

    @Test
    @DisplayName("페이지를 이어 받아도 발화가 빠지거나 겹치지 않는다")
    void 페이지를_이어받아도_전체가_한_번씩_나온다() {
        for (int i = 1; i <= 7; i++) {
            insert(i, MEETING, i, i * 100, "발화 " + i);
        }
        insert(8L, MEETING, 8, null, "오프셋 없음 A");
        insert(9L, MEETING, 9, null, "오프셋 없음 B");

        List<String> collected = readAllByPaging(3);

        assertThat(collected).containsExactly(
                "발화 1", "발화 2", "발화 3", "발화 4", "발화 5", "발화 6", "발화 7",
                "오프셋 없음 A", "오프셋 없음 B");
    }

    @Test
    @DisplayName("오프셋이 같은 발화가 페이지 경계에 걸려도 남은 것이 따라 나온다")
    void 같은_오프셋이_경계에_걸려도_빠지지_않는다() {
        // 커서를 오프셋 하나로만 잡으면 여기서 seq 2·3 이 통째로 사라진다 —
        // 커서를 (offsetMs, seq) 두 값으로 둔 이유가 이 자리다.
        insert(1L, MEETING, 1, 100, "같은 오프셋 A");
        insert(2L, MEETING, 2, 100, "같은 오프셋 B");
        insert(3L, MEETING, 3, 100, "같은 오프셋 C");
        insert(4L, MEETING, 4, 200, "다음 오프셋");

        List<String> collected = readAllByPaging(1);

        assertThat(collected).containsExactly(
                "같은 오프셋 A", "같은 오프셋 B", "같은 오프셋 C", "다음 오프셋");
    }

    @Test
    @DisplayName("꼬리 구간 안에서도 이어 받는다")
    void 꼬리_구간_커서로_이어받는다() {
        insert(1L, MEETING, 1, 100, "본문");
        insert(2L, MEETING, 2, null, "꼬리 A");
        insert(3L, MEETING, 3, null, "꼬리 B");
        insert(4L, MEETING, 4, null, "꼬리 C");

        // 꼬리 한복판을 가리키는 커서 — offsetMs 가 null 인 것이 곧 "꼬리 구간"이라는 표시다.
        List<UtteranceView> page = transcriptRepository.findPage(MEETING, new TranscriptCursor(null, 2), 10);

        assertThat(page).extracting(UtteranceView::content).containsExactly("꼬리 B", "꼬리 C");
    }

    @Test
    @DisplayName("다른 회의 발화는 섞이지 않는다")
    void 다른_회의_발화는_섞이지_않는다() {
        insert(1L, MEETING, 1, 100, "우리 회의");
        insert(2L, OTHER_MEETING, 1, 50, "남의 회의");

        assertThat(transcriptRepository.findPage(MEETING, null, 10))
                .extracting(UtteranceView::content)
                .containsExactly("우리 회의");
    }

    @Test
    @DisplayName("ids 조회는 회의 밖 발화를 돌려주지 않는다 — id 만으로 찾으면 남의 회의 원문이 실린다")
    void ids_조회도_회의를_함께_건다() {
        insert(1L, MEETING, 1, 100, "우리 회의");
        insert(2L, OTHER_MEETING, 1, 50, "남의 회의");

        List<UtteranceView> selected = transcriptRepository.findByMeetingAndIds(MEETING, List.of(1L, 2L));

        // 요청한 id 중 그 회의에 없는 것은 조용히 빠진다(404 로 세우지 않는다).
        assertThat(selected).extracting(UtteranceView::content).containsExactly("우리 회의");
    }

    @Test
    @DisplayName("ids 응답은 요청 순서가 아니라 정본 순서다")
    void ids_응답은_정본_순서다() {
        insert(1L, MEETING, 1, 300, "나중 발화");
        insert(2L, MEETING, 2, 100, "먼저 발화");

        List<UtteranceView> selected = transcriptRepository.findByMeetingAndIds(MEETING, List.of(1L, 2L));

        assertThat(selected).extracting(UtteranceView::content).containsExactly("먼저 발화", "나중 발화");
    }

    /* 페이지 크기 pageSize 로 끝까지 훑는다. 실제 클라이언트가 커서를 되돌려주는 흐름 그대로다. */
    private List<String> readAllByPaging(int pageSize) {
        List<String> collected = new ArrayList<>();
        TranscriptCursor cursor = null;
        while (true) {
            List<UtteranceView> page = transcriptRepository.findPage(MEETING, cursor, pageSize);
            page.forEach(view -> collected.add(view.content()));
            if (page.size() < pageSize) {
                return collected;
            }
            UtteranceView last = page.get(page.size() - 1);
            cursor = new TranscriptCursor(last.startOffsetMs(), last.seq());
        }
    }

    private void insert(long id, long meetingId, int seq, Integer offsetMs, String content) {
        jdbcTemplate.update(
                "INSERT INTO transcript_chunk (id, meeting_id, seq, content, offset_ms) VALUES (?, ?, ?, ?, ?)",
                id, meetingId, seq, content, offsetMs);
    }
}
