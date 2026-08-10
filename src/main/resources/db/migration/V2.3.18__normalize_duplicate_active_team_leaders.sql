-- =====================================================================
-- V2.3.18 : 한 부서에 활성 팀장이 둘 이상인 데이터를 정리한다   [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   "팀당 팀장 한 명"은 지금 team.leader_member_id 컬럼이 하나라는 사실로만
--   보장된다. member.authority 쪽은 아무도 세지 않아서, 두 표현이 어긋나면
--   (권한은 LEADER 인데 부서의 팀장 참조는 다른 사람을 가리키는 상태)
--   아무 데서도 걸리지 않는다. V2.3.19 가 그 카운트를 데이터베이스 제약으로
--   세우는데, 이미 어긋난 행이 남아 있으면 그 ALTER 가 실패한다.
--
--   어긋나는 경로는 실제로 있었다. 오프보딩이 팀장 자리를 비우지 않던 시절의
--   퇴사자, 셀프 프로필로 팀을 옮기며 강등된 사람 등이 남긴 흔적이다.
--
-- 남길 사람을 고르는 규칙:
--   1순위 — 부서의 team.leader_member_id 가 가리키는 사람. 후임 승급 검사가
--          보는 것이 이 컬럼이므로(MemberIssuer#persist), 그쪽을 정답으로 삼아야
--          정리 후에도 화면과 검사가 같은 사람을 팀장으로 본다.
--   2순위 — 팀장 참조가 비었거나 이 팀 사람이 아니면 가장 먼저 합류한 사람,
--          즉 joined_on 이 가장 이른 사람(동률이면 id 로 결정한다). id 순서는
--          합류 순서를 보장하지 않는다 — 데이터 이관이나 수동 id 지정 이력이
--          있으면 최소 id가 실제로는 나중에 합류한 사람일 수 있다. joined_on 이
--          비어 있는(NULL) 행은 합류 시점을 모르는 것이므로 후순위로 민다(다른
--          모든 후보가 처리된 뒤에야 고려한다).
--
--   활성 팀장이 한 명뿐인 부서는 건드리지 않는다 — 제약을 위반하지 않으므로
--   굳이 강등할 이유가 없다. 자리가 비어 있는데 권한만 LEADER 인 사람도
--   그대로 둔다(위반이 아니다).
--
-- 확인 쿼리(적용 후 0건이어야 한다):
--   SELECT team_id, COUNT(*)
--   FROM member
--   WHERE authority = 'LEADER' AND deleted_at IS NULL AND team_id IS NOT NULL
--   GROUP BY team_id
--   HAVING COUNT(*) > 1;
-- =====================================================================

UPDATE `member` AS m
JOIN (
    SELECT dup.team_id AS team_id,
           COALESCE(
               MIN(CASE WHEN t.leader_member_id = a.id THEN a.id END),
               /* GROUP_CONCAT + SUBSTRING_INDEX 로 "joined_on 최솟값을 가진 행의 id"를
                  구한다(MySQL 에 ORDER BY 를 지원하는 arg-min 집계 함수가 없다). NULL을
                  마지막으로 미루려고 (joined_on IS NULL) 을 1차 정렬 키로 넣는다. */
               CAST(
                   SUBSTRING_INDEX(
                       GROUP_CONCAT(a.id ORDER BY (a.joined_on IS NULL) ASC, a.joined_on ASC, a.id ASC),
                       ',', 1
                   ) AS UNSIGNED
               )
           ) AS keeper_id
    FROM `member` AS a
    JOIN (
        SELECT team_id
        FROM `member`
        WHERE authority = 'LEADER'
          AND deleted_at IS NULL
          AND team_id IS NOT NULL
        GROUP BY team_id
        HAVING COUNT(*) > 1
    ) AS dup ON dup.team_id = a.team_id
    LEFT JOIN `team` AS t ON t.id = a.team_id
    WHERE a.authority = 'LEADER'
      AND a.deleted_at IS NULL
    GROUP BY dup.team_id
) AS keep ON keep.team_id = m.team_id
SET m.authority = 'MEMBER'
WHERE m.authority = 'LEADER'
  AND m.deleted_at IS NULL
  AND m.id <> keep.keeper_id;
