-- =====================================================================
-- V6.6.1 : 히어로 회의 데모 시드 — 회의 1건의 산출물 묶음 전체
-- ---------------------------------------------------------------------
-- 발표 시연용. "회의가 끝나면 AI가 액션을 뽑아 담당자에게 자동 배정한다"를
-- 화면으로 보여주기 위한 데이터다. 라이브 파이프라인(STT·LLM)을 돌리지 않는다 —
-- 운영 프로파일이 prod 하드코딩이라 실제 AWS Transcribe/AI 레이어가 붙고,
-- 2026-08-16 테스트에서 회의 4건이 전부 요약 실패했다(429 추정). 시연 당일에
-- 같은 일이 나면 복구할 시간이 없다.
--
-- ---------------------------------------------------------------------
-- 왜 V6.6.1 인가 (레인 선택 근거 — 다시 헷갈리지 않도록 남긴다)
-- ---------------------------------------------------------------------
-- DB_MIGRATION_RULES.md §2 는 **담당자별** 메이저 버전이다(김민섭 = V6.x.y).
-- "테이블별로 레인이 갈린다"는 해석이 한때 돌았으나 틀렸다 —
-- V5.17__add_dispatched_at_to_action.sql(이태연)이 action 테이블을 건드린 것이
-- 그 해석의 반례다. V2.6.x 가 전부 김민섭인 것은 윤종호 레인을 침범한
-- 과거의 실수이고, 이미 머지돼 되돌릴 수 없을 뿐이다(§3).
--
-- 그리고 이 파일은 **낮은 레인으로 갈 수 없다**(§5). 아래 테이블에 의존한다:
--     meeting_decision   V5.8
--     meeting_summary    V5.7
--     transcript_chunk.speaker_member_id  V5.3
--     meeting_assignment_tuple            V5.12 ~ V5.15 · V5.22
--     caption_chunk      V5.2
--     recording.stt_triggered             V4.2.2
--     action.gate_signals                 V2.6.1
-- 빈 DB 는 버전 순서대로 적용하므로 V2.6.11 로 썼다면 meeting_decision 이
-- 아직 없는 시점에 실행돼 실패한다. 운영·CI 는 FLYWAY_OUT_OF_ORDER=false 라
-- 우회도 없다. 자기 레인의 최신(V6.5.3) 다음 묶음이라 가운데 자리를 올렸다(§2).
--
-- ---------------------------------------------------------------------
-- 왜 여러 테이블을 한 파일에 담는가 (§8-2 와 충돌하지 않는다)
-- ---------------------------------------------------------------------
-- §8-2 의 근거는 "MySQL 은 transactional DDL 을 지원하지 않아, 한 파일의 세
-- 번째 DDL 이 실패해도 앞의 둘은 이미 커밋된다"이다. 이 파일에는 DDL 이 하나도
-- 없다. 순수 INSERT 는 InnoDB 트랜잭션으로 묶여 통째로 롤백되므로 반쯤 적용된
-- 상태가 생기지 않는다 — 오히려 회의 한 건의 산출물을 쪼개면 그 사이에서
-- 깨질 수 있다. §10 은 "일회성 시드는 versioned(자기 레인)로" 라고 명시
-- 허용하며, 선례는 V2.3.9__seed_system_roles.sql 이다. R__ 는 쓰지 않는다.
--
-- ---------------------------------------------------------------------
-- ⚠ 왜 전부 INSERT ... SELECT 인가 (이 파일에서 가장 중요한 설계)
-- ---------------------------------------------------------------------
-- 이 시드는 **화면에서 손으로 만든 데이터**(company · project · member ·
-- meeting_room)에 얹힌다. 그런데 Flyway 는 CI 와 새 로컬처럼 **빈 DB 에서도**
-- 실행된다. 거기엔 그 데이터가 없다.
--
--   VALUES ((SELECT id FROM company WHERE code = '...'), ...)  ← 이렇게 쓰면
--   빈 DB 에서 스칼라 서브쿼리가 NULL 을 돌려주고, company_id 는 NOT NULL 이라
--   마이그레이션이 실패한다. 그 순간 앱이 부팅되지 않고 CI migrationCheck 가
--   깨진다. 데모 데이터 하나 넣자고 전 환경을 세우는 셈이다.
--
-- 그래서 모든 INSERT 를 SELECT 기반으로 쓴다. 앵커(회사·프로젝트·멤버)가
-- 없으면 **0행이 매칭돼 아무 일도 일어나지 않는다**(no-op). 실패하지 않는다.
--
-- 이 설계의 부수 효과가 하나 더 있다 — 데모 데이터가 마이그레이션 히스토리에
-- 영원히 남더라도 **실제로 행이 생기는 환경은 앵커가 존재하는 그 DB 뿐**이다.
-- 새로 세팅하는 로컬·CI·스테이징은 깨끗하다.
--
-- 또한 이 파일은 첫 INSERT(meeting)에서만 중복을 막고, 나머지는 그 회의를
-- 앵커로 삼아 사슬처럼 이어진다. 회의가 안 들어가면 뒤도 전부 no-op 이다.
--
-- ---------------------------------------------------------------------
-- ⚠ 화면에서 만들 때 아래 값과 **정확히** 일치시킬 것
-- ---------------------------------------------------------------------
-- 하나라도 다르면 조용히 no-op 이 된다(에러가 나지 않는다. 이게 이 설계의 대가다).
-- 배포 후 반드시 회의 상세 화면으로 눈으로 확인할 것.
--
--   company.code       = 'TGXJ-BZY3'
--   project.tag        = 'AIGRP'
--   meeting_room.name  = '백엔드 강의실'   (location 은 '박애관 623호' — 별도 컬럼이라 JOIN 에 넣지 않는다)
--   member.email       = 'frozenahri302@gmail.com'      (회의 담당자 · OWNER · 참석자1)
--                        'mnppi223@gmail.com'           (참석자2)
--                        'frozenahri302@g.eulji.ac.kr'  (참석자3)
--
-- 그리고 아래 제목의 회의는 **화면에서 만들지 말 것**. 이 파일이 만든다.
--   meeting.title = '8월 데모 준비 현황 점검'
--
-- 배포 순서 제약: 이 마이그레이션이 실행되는 배포 **이전에** 위 기반
-- 데이터(회사·프로젝트·멤버·회의실)가 그 DB 에 이미 있어야 한다.
--
-- ---------------------------------------------------------------------
-- ⚠ review_status 는 PENDING 이다. AUTO_CONFIRMED 를 쓰지 않는다
-- ---------------------------------------------------------------------
-- action.review_status 컬럼의 ENUM 에는 'AUTO_CONFIRMED' 문자열이 아직 남아
-- 있지만(V2.6.2 에서 추가, 드롭한 마이그레이션 없음), Java 쪽
-- ActionReviewStatus 에서는 2026-08-12 에 제거됐다(이슈 #415).
-- @Enumerated(STRING) 이라 그 값을 넣으면 INSERT 는 성공하고 **읽는 순간**
-- valueOf 가 터진다 — 조회가 죽는다. 절대 넣지 않는다.
--
-- 자동확정 표현은 두 곳에 나눠 싣는다:
--   1) meeting_assignment_tuple.gate_* 컬럼  ← 화면이 실제로 읽는 곳
--   2) action.gate_signals JSON              ← C 도메인이 갖는 사본
-- 1번이 없으면 시연이 정반대로 나온다. 아래 tuple 절의 주석 참조.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. meeting — 종료된 대면 회의 1건
-- ---------------------------------------------------------------------
-- team_id 는 NULL 이다(OWNER 가 개설한 회의). is_online=FALSE 라 회의실이 붙는다.
-- started_at·ended_at 을 실제로 채운다 — 자동분석 트리거가 3분 미만 회의를
-- 건너뛰므로, 시연 중 누가 "다시 분석"을 눌러도 길이 때문에 스킵되지는 않게 한다.
-- NOT EXISTS 는 같은 제목의 회의가 이미 손으로 만들어져 있을 때의 방어선이다.
INSERT INTO `meeting` (`company_id`, `project_id`, `team_id`, `meeting_room_id`,
                       `host_member_id`, `title`, `status`, `start_at`, `end_at`,
                       `is_online`, `recording_consent`, `started_at`, `ended_at`)
SELECT c.id,
       p.id,
       NULL,
       r.id,
       h.id,
       '8월 데모 준비 현황 점검',
       'DONE',
       '2026-08-14 10:00:00',
       '2026-08-14 11:30:00',
       FALSE,
       TRUE,
       '2026-08-14 10:02:00',
       '2026-08-14 11:28:00'
  FROM `company` c
  JOIN `project` p       ON p.company_id = c.id AND p.tag = 'AIGRP' AND p.deleted_at IS NULL
  JOIN `meeting_room` r  ON r.company_id = c.id AND r.name = '백엔드 강의실' AND r.deleted_at IS NULL
  JOIN `member` h        ON h.company_id = c.id AND h.email = 'frozenahri302@gmail.com' AND h.deleted_at IS NULL
 WHERE c.code = 'TGXJ-BZY3'
   AND NOT EXISTS (SELECT 1
                     FROM `meeting` m2
                    WHERE m2.company_id = c.id
                      AND m2.title = '8월 데모 준비 현황 점검');


-- ---------------------------------------------------------------------
-- 2. meeting_attendee — 참석자 3명 (회의 담당자 본인 포함)
-- ---------------------------------------------------------------------
-- 담당자가 참석자 명단 안에 있어야 게이트 조건2(assigneeInRoster)가 성립한다.
-- 아래 액션 3건의 담당자가 모두 이 셋 안에 있는 이유다.
INSERT INTO `meeting_attendee` (`meeting_id`, `member_id`)
SELECT m.id, a.id
  FROM `meeting` m
  JOIN `company` c ON c.id = m.company_id
  JOIN `member` a  ON a.company_id = c.id
                  AND a.deleted_at IS NULL
                  AND a.email IN ('frozenahri302@gmail.com', 'mnppi223@gmail.com', 'frozenahri302@g.eulji.ac.kr')
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';


-- ---------------------------------------------------------------------
-- 3. meeting_topic — 대주제 1 + 소주제 1
-- ---------------------------------------------------------------------
-- 소주제가 대주제를 parent_topic_id 로 참조하므로 두 문장으로 나눈다.
-- (한 문장으로는 방금 넣은 행의 id 를 같은 INSERT 안에서 쓸 수 없다.)
INSERT INTO `meeting_topic` (`meeting_id`, `parent_topic_id`, `topic_type`, `content`, `sort_order`)
SELECT m.id, NULL, 'MAIN', '8월 데모 준비 현황 점검', 1
  FROM `meeting` m
  JOIN `company` c ON c.id = m.company_id
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';

INSERT INTO `meeting_topic` (`meeting_id`, `parent_topic_id`, `topic_type`, `content`, `sort_order`)
SELECT m.id, t.id, 'SUB', '결제 모듈 명세와 약관 개정 일정', 1
  FROM `meeting` m
  JOIN `company` c       ON c.id = m.company_id
  JOIN `meeting_topic` t ON t.meeting_id = m.id AND t.topic_type = 'MAIN' AND t.sort_order = 1
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';


-- ---------------------------------------------------------------------
-- 4. recording — 통합 녹음본 1건
-- ---------------------------------------------------------------------
-- ⚠ file_url 은 실재하지 않는 S3 키다. 회의 상세에서 **재생 버튼을 누르면 깨진다.**
--    시연 동선에서 녹음 재생은 빼거나, 실제 파일을 같은 키로 올려둘 것.
--    여기서 진짜 URL 을 만들 방법은 없다(우리에게 S3 접근 권한이 없다).
-- stt_triggered=1 로 둔다. 0 이면 "아직 STT 안 돌린 녹음"으로 읽혀,
-- 시연 중 자동으로 STT 가 트리거될 여지를 남긴다 — 그건 실비용이 나가는 경로다.
INSERT INTO `recording` (`meeting_id`, `file_name`, `file_url`, `file_size`, `duration_sec`, `stt_triggered`)
SELECT m.id,
       'hero-meeting-20260814.m4a',
       'meetings/demo/hero-meeting-20260814.m4a',
       41287680,
       5160,
       1
  FROM `meeting` m
  JOIN `company` c ON c.id = m.company_id
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';


-- ---------------------------------------------------------------------
-- 5. transcript_chunk — 자막 정본 5줄
-- ---------------------------------------------------------------------
-- 검토 화면이 근거 발화를 여기서 조인해 온다
-- (ActionReviewJdbcAdapter: LEFT JOIN transcript_chunk tc ON tc.id = a.evidence_transcript_id
--  AND tc.meeting_id = a.source_meeting_id). 이 5줄이 없으면 액션 카드의
-- "근거 발화"가 빈칸으로 뜬다 — 정확도 4원칙의 첫 번째가 화면에서 사라진다.
--
-- speaker_member_id 를 반드시 채운다. 게이트 조건3(assigneeSourceOk)이
-- "1인칭이면서 근거 발화의 화자가 확정됐다"를 요구하기 때문이다. NULL 이면
-- "제가 하겠습니다"의 '제가'가 누군지 모르는 상태라 근거가 성립하지 않는다.
INSERT INTO `transcript_chunk` (`meeting_id`, `seq`, `content`, `offset_ms`, `end_offset_ms`,
                                `speaker_member_id`, `speaker_source`, `stt_block_seq`)
SELECT m.id, v.seq, v.content, v.offset_ms, v.end_offset_ms, spk.id, 'SELF_STREAM', v.blk
  FROM `meeting` m
  JOIN `company` c ON c.id = m.company_id
 CROSS JOIN (
        SELECT 1 AS seq, '오늘은 8월 데모 준비 상황을 정리하겠습니다. 각자 맡은 부분 공유해 주세요.' AS content,
               15000 AS offset_ms, 32000 AS end_offset_ms, 1 AS blk, 'frozenahri302@gmail.com' AS email
  UNION ALL SELECT 2, '결제 모듈 API 명세는 제가 이번 주 안에 초안까지 마무리하겠습니다.',
               61000, 79000, 1, 'mnppi223@gmail.com'
  UNION ALL SELECT 3, '좋습니다. 그러면 프론트 담당자분이 이용약관 개정안 법무 검토를 다음 주 화요일까지 넘겨 주세요.',
               132000, 151000, 2, 'frozenahri302@gmail.com'
  UNION ALL SELECT 4, '네, 이용약관 개정안은 화요일까지 법무팀에 전달하겠습니다.',
               158000, 174000, 2, 'frozenahri302@g.eulji.ac.kr'
  UNION ALL SELECT 5, '그리고 베타 테스터 모집 공고는 제가 직접 올리겠습니다.',
               240000, 259000, 3, 'frozenahri302@gmail.com'
       ) v
  JOIN `member` spk ON spk.company_id = c.id AND spk.email = v.email AND spk.deleted_at IS NULL
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';


-- ---------------------------------------------------------------------
-- 6. caption_chunk — 실시간 자막 5줄 (같은 발화의 실시간 경로 사본)
-- ---------------------------------------------------------------------
-- 자막 탭이 어느 엔드포인트를 부르는지가 아직 확정되지 않아 양쪽을 다 채운다.
--   GET /api/meetings/{id}/transcripts → transcript_chunk (위 5번)
--   GET /api/meetings/{id}/captions    → caption_chunk    (여기)
-- 어느 쪽을 부르든 자막 탭이 비지 않는다. 확정되면 불필요한 쪽을 지우는 게
-- 아니라 그냥 두면 된다 — 실제 회의에서도 두 경로의 데이터는 함께 존재한다.
--
-- 제약 셋을 지켜야 한다(V5.2 CHECK): rms <= 0(dBFS 라 0 이 최대),
-- start_offset_ms >= 0, end_offset_ms >= start_offset_ms.
-- seq 는 **참석자별** 순번이다(UNIQUE(meeting_id, member_id, seq)) —
-- transcript 의 통짜 순번과 다르다. owner 가 3번 말했으므로 1·2·3 을 갖는다.
INSERT INTO `caption_chunk` (`meeting_id`, `member_id`, `seq`, `start_offset_ms`, `end_offset_ms`, `text`, `rms`)
SELECT m.id, spk.id, v.seq, v.start_offset_ms, v.end_offset_ms, v.`text`, v.rms
  FROM `meeting` m
  JOIN `company` c ON c.id = m.company_id
 CROSS JOIN (
        SELECT 'frozenahri302@gmail.com' AS email, 1 AS seq, 15000 AS start_offset_ms, 32000 AS end_offset_ms,
               '오늘은 8월 데모 준비 상황을 정리하겠습니다. 각자 맡은 부분 공유해 주세요.' AS `text`, -18.40 AS rms
  UNION ALL SELECT 'mnppi223@gmail.com',   1,  61000,  79000, '결제 모듈 API 명세는 제가 이번 주 안에 초안까지 마무리하겠습니다.', -21.30
  UNION ALL SELECT 'frozenahri302@gmail.com', 2, 132000, 151000, '좋습니다. 그러면 프론트 담당자분이 이용약관 개정안 법무 검토를 다음 주 화요일까지 넘겨 주세요.', -17.90
  UNION ALL SELECT 'frozenahri302@g.eulji.ac.kr',   1, 158000, 174000, '네, 이용약관 개정안은 화요일까지 법무팀에 전달하겠습니다.', -22.60
  UNION ALL SELECT 'frozenahri302@gmail.com', 3, 240000, 259000, '그리고 베타 테스터 모집 공고는 제가 직접 올리겠습니다.', -19.10
       ) v
  JOIN `member` spk ON spk.company_id = c.id AND spk.email = v.email AND spk.deleted_at IS NULL
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';


-- ---------------------------------------------------------------------
-- 7. meeting_summary — 요약 본문 1건
-- ---------------------------------------------------------------------
-- edited_at 은 NULL 이다 — 사람이 손대지 않은 AI 생성 원본이라는 뜻이고,
-- 시연에서 "AI 가 만든 그대로"를 주장하려면 이 값이 NULL 이어야 한다.
-- 회의당 1건 UNIQUE(UK_MEETING_SUMMARY_MEETING).
INSERT INTO `meeting_summary` (`company_id`, `meeting_id`, `overview`, `model_name`, `prompt_version`)
SELECT c.id,
       m.id,
       '8월 데모 준비 현황을 점검했다. 결제 모듈 API 명세는 이번 주 내 초안을 확정하기로 했고, 이용약관 개정안은 다음 주 화요일까지 법무 검토에 넘기기로 했다. 베타 테스터 모집 공고는 회의 담당자가 직접 게시하기로 했다.',
       'gemini-2.5-pro',
       'v1.3'
  FROM `meeting` m
  JOIN `company` c ON c.id = m.company_id
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';


-- ---------------------------------------------------------------------
-- 8. meeting_decision — 결정사항 2건
-- ---------------------------------------------------------------------
-- FK 가 (meeting_summary_id, meeting_id) 복합이다. 요약의 존재만이 아니라
-- "같은 회의인지"까지 강제한다 — 아래 조인이 m.id 로 양쪽을 묶는 이유다.
-- gate_status='CONFIRMED' 인 항목만 L4(tuple 추출)로 넘어가므로, 이 둘이
-- 아래 10번 tuple 의 출처가 된다.
INSERT INTO `meeting_decision` (`meeting_summary_id`, `meeting_id`, `topic_seq`, `topic`,
                                `item_type`, `content`, `reason`, `evidence_transcript_id`,
                                `gate_status`, `sort_order`)
SELECT s.id, m.id, 1, v.topic, 'DECISION', v.content, v.reason, tc.id, 'CONFIRMED', v.sort_order
  FROM `meeting` m
  JOIN `company` c           ON c.id = m.company_id
  JOIN `meeting_summary` s   ON s.meeting_id = m.id
 CROSS JOIN (
        SELECT '결제 모듈 명세' AS topic,
               '결제 모듈 API 명세 초안을 이번 주 내로 확정한다.' AS content,
               '담당자가 기한과 산출물을 함께 명시해 확정 발화로 판단했다.' AS reason,
               2 AS evidence_seq, 1 AS sort_order
  UNION ALL SELECT '약관 개정 일정',
               '이용약관 개정안을 다음 주 화요일까지 법무 검토에 넘긴다.',
               '요청과 수락이 연속된 발화로 확인돼 확정 발화로 판단했다.',
               3, 2
       ) v
  JOIN `transcript_chunk` tc ON tc.meeting_id = m.id AND tc.seq = v.evidence_seq
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';


-- ---------------------------------------------------------------------
-- 9. action — 회의에서 도출된 액션 3건 (자동할당 시연의 본체)
-- ---------------------------------------------------------------------
-- source_meeting_id 가 이 회의를 가리켜야 검토 화면(GET /api/meetings/{id}/review)이
-- 이 액션들을 찾는다 — 그 SQL 의 WHERE 절이 a.source_meeting_id = ? 다.
--
-- review_status 는 PENDING 이다(파일 상단 주석 참조). is_manual=FALSE 는
-- "사람이 + 로 추가한 게 아니라 파이프라인이 만들었다"는 표시이고, 화면이
-- AI 산출물과 수기 추가를 가르는 기준이다.
-- due_date_defaulted=FALSE 는 "AI 가 기한을 직접 지정했다"는 뜻이다 —
-- TRUE 면 화면이 "프로젝트 마감일로 채웠음" 배지를 붙인다.
--
-- gate_signals 는 C 도메인이 갖는 사본이다. 키 이름과 순서는
-- TupleDistributionService.gateSignalsJson() 이 만드는 것과 정확히 같게 둔다
-- (hasEvidence · assigneeInRoster · assigneeSourceOk · viewsAgree · autoConfirmed).
-- ⚠ 화면은 이 JSON 을 읽지 않는다. 읽는 곳은 10번의 tuple 이다.
INSERT INTO `action` (`company_id`, `project_id`, `source_meeting_id`, `team_id`,
                      `assignee_member_id`, `action_type`, `title`, `description`,
                      `status`, `due_date`, `planned_start_date`, `start_date`, `is_done`,
                      `review_status`, `assignee_source`, `evidence_transcript_id`,
                      `gate_signals`, `is_manual`, `due_date_defaulted`, `dispatched_at`)
SELECT c.id, m.project_id, m.id, NULL, asg.id, 'PERSONAL',
       v.title, v.description, v.`status`, v.due_date, v.planned_start_date, v.start_date,
       v.is_done, 'PENDING', v.assignee_source, tc.id,
       '{"hasEvidence": true, "assigneeInRoster": true, "assigneeSourceOk": true, "viewsAgree": true, "autoConfirmed": true}',
       FALSE, FALSE, '2026-08-14 11:35:00'
  FROM `meeting` m
  JOIN `company` c ON c.id = m.company_id
 CROSS JOIN (
        SELECT 'mnppi223@gmail.com' AS email,
               '결제 모듈 API 명세 초안 작성' AS title,
               '결제 모듈의 요청·응답 스키마와 오류 코드를 포함한 API 명세 초안을 작성한다.' AS description,
               'IN_PROGRESS' AS `status`, DATE '2026-08-21' AS due_date,
               DATE '2026-08-14' AS planned_start_date, DATE '2026-08-14' AS start_date,
               FALSE AS is_done, 'FIRST_PERSON' AS assignee_source, 2 AS evidence_seq
  UNION ALL SELECT 'frozenahri302@g.eulji.ac.kr',
               '이용약관 개정안 법무 검토 요청',
               '개정된 이용약관 초안을 법무팀에 전달하고 검토 회신을 받는다.',
               'TODO', DATE '2026-08-18',
               DATE '2026-08-17', NULL,
               FALSE, 'EXPLICIT_CALL', 3
  UNION ALL SELECT 'frozenahri302@gmail.com',
               '베타 테스터 모집 공고 게시',
               '사내 공지와 외부 채널에 베타 테스터 모집 공고를 게시한다.',
               'DONE', DATE '2026-08-15',
               DATE '2026-08-14', DATE '2026-08-14',
               TRUE, 'FIRST_PERSON', 5
       ) v
  JOIN `member` asg          ON asg.company_id = c.id AND asg.email = v.email AND asg.deleted_at IS NULL
  JOIN `transcript_chunk` tc ON tc.meeting_id = m.id AND tc.seq = v.evidence_seq
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';


-- ---------------------------------------------------------------------
-- 10. meeting_assignment_tuple — 게이트 판정 원본 3건
-- ---------------------------------------------------------------------
-- ⚠ 이 절이 없으면 시연이 정확히 반대로 나온다. 빼먹지 말 것.
--
-- 검토 화면은 게이트 신호를 action.gate_signals(JSON)에서 읽지 않는다.
-- ActionReviewJdbcAdapter 의 SQL 이 meeting_assignment_tuple 을 LEFT JOIN 해
-- t.gate_auto_confirmed 를 읽고, ActionReviewService 가
--     .filter(action -> !Boolean.TRUE.equals(action.autoConfirmed()))
-- 로 검토 대상을 고른다. tuple 행이 없으면 그 값이 NULL 이고,
-- !TRUE.equals(null) 은 true 라 **액션 3건이 전부 「AI 확인 필요」 묶음에 뜬다.**
-- 서비스 주석도 "게이트를 아예 안 지난 것(null)"을 검토 대상으로 명시한다.
-- 자동할당을 보여주려는 시연에서 정반대 화면이 나오는 것이다.
--
-- action_id 에 UNIQUE(V5.15)가 걸려 있어 액션당 정확히 1행이다.
-- 세 번째 tuple 의 meeting_decision_id 는 NULL 이다 — 결정사항은 2건인데
-- 액션은 3건이라 대응할 항목이 없다. 컬럼이 NULL 을 허용하고 화면도 이 값을
-- 읽지 않으므로 무해하다. 결정사항을 3건으로 늘리는 건 원 스펙(결정사항 2)을
-- 바꾸는 일이라 하지 않았다.
INSERT INTO `meeting_assignment_tuple`
       (`company_id`, `meeting_id`, `meeting_decision_id`, `topic_seq`, `topic`, `title`,
        `assignee_candidate_member_id`, `assignee_source`, `due_date`, `evidence_transcript_id`,
        `model_name`, `prompt_version`, `action_id`, `sort_order`,
        `verify_agree`, `verify_verdict`, `verify_reason`, `verify_model_name`,
        `verify_prompt_version`, `verified_at`,
        `conflicts`, `conflict_checked_at`,
        `gate_auto_confirmed`, `gate_has_evidence`, `gate_assignee_in_roster`,
        `gate_assignee_source_ok`, `gate_views_agree`, `gated_at`,
        `assignee_near_matched`)
SELECT c.id, m.id, d.id, 1, v.topic, v.title,
       asg.id, v.assignee_source, v.due_date, tc.id,
       'gemini-2.5-pro', 'v1.3', a.id, v.sort_order,
       TRUE, 'ACCEPT', '두 관점이 담당자와 기한에서 일치했다.', 'gemini-2.5-pro',
       'v1.3', '2026-08-14 11:33:00',
       NULL, '2026-08-14 11:34:00',
       TRUE, TRUE, TRUE,
       TRUE, TRUE, '2026-08-14 11:34:30',
       FALSE
  FROM `meeting` m
  JOIN `company` c ON c.id = m.company_id
 CROSS JOIN (
        SELECT '결제 모듈 명세' AS topic, '결제 모듈 API 명세 초안 작성' AS title,
               'mnppi223@gmail.com' AS email, 'FIRST_PERSON' AS assignee_source,
               DATE '2026-08-21' AS due_date, 2 AS evidence_seq,
               1 AS dec_sort, 1 AS sort_order
  UNION ALL SELECT '약관 개정 일정', '이용약관 개정안 법무 검토 요청',
               'frozenahri302@g.eulji.ac.kr', 'EXPLICIT_CALL',
               DATE '2026-08-18', 3,
               2, 2
  UNION ALL SELECT '8월 데모 준비 현황 점검', '베타 테스터 모집 공고 게시',
               'frozenahri302@gmail.com', 'FIRST_PERSON',
               DATE '2026-08-15', 5,
               NULL, 3
       ) v
  JOIN `member` asg          ON asg.company_id = c.id AND asg.email = v.email AND asg.deleted_at IS NULL
  JOIN `transcript_chunk` tc ON tc.meeting_id = m.id AND tc.seq = v.evidence_seq
  JOIN `action` a            ON a.source_meeting_id = m.id AND a.title = v.title
  LEFT JOIN `meeting_decision` d ON d.meeting_id = m.id AND d.sort_order = v.dec_sort
 WHERE c.code = 'TGXJ-BZY3'
   AND m.title = '8월 데모 준비 현황 점검';
