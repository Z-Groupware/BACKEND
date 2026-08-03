# Flyway Migration 운영 규칙 (module06)

> 대상: 백엔드 6인 협업 (Java 17 · Spring Boot · MySQL 8 · Flyway)
> 목적: 담당자별 버전 분리 + out-of-order 허용 + 운영 배포 안정성
> 기준: 첨부 ERD(Module06 Ver 1.1.0)를 **V1 baseline**으로 고정, 이후 담당자별 V2.N.x

---

## 1. 기본 정책

- 초기 전체 스키마는 **`V1__init_schema.sql`** 하나에 담는다(baseline).
- 이후 모든 신규 DB 변경은 **담당자별 V2.N.x 영역**에서 증분으로만 추가한다.
- **baseline 처리 (확정값)**: 운영 DB에는 이미 초기 스키마가 적용돼 있을 수 있으므로 히스토리 어긋남을 막는다.
  - `spring.flyway.baseline-on-migrate=true`
  - `spring.flyway.baseline-version=1`
  - 완전히 빈 DB → baseline 대상 아님 → `V1`이 그대로 실행되어 스키마 생성
  - 기존 스키마가 있는 DB → `V1`을 baseline으로 등록(재실행 안 함) → `V2.x`부터 적용

---

## 2. 담당자별 버전 영역

각 담당자는 **배정받은 영역만** 사용한다. (버전 = `V2.{담당자}.{순번}`)

| 담당자 | 버전 영역 | 예시 |
| --- | --- | --- |
| 박종준 | `V2.1.x` | `V2.1.1__...sql` |
| 윤종호 | `V2.2.x` | `V2.2.1__...sql` |
| 모성진 | `V2.3.x` | `V2.3.1__...sql` |
| 김현지 | `V2.4.x` | `V2.4.1__...sql` |
| 이태연 | `V2.5.x` | `V2.5.1__...sql` |
| 김민섭 | `V2.6.x` | `V2.6.1__...sql` |

> Flyway는 동일 버전 번호를 허용하지 않으므로, 영역 분리가 파일명/버전 충돌을 원천 차단한다.
> 순번은 자기 레인 안에서 1부터 증가시킨다 (`V2.2.1`, `V2.2.2`, ...).

---

## 3. Migration 파일 수정 금지

이미 Push/Merge된 Migration 파일은 **수정하지 않는다.**
Flyway는 적용 시 checksum을 `flyway_schema_history`에 저장하며, 적용된 파일을 고치면 checksum 불일치로 `validate`가 실패한다(팀 규칙이 아니라 Flyway의 물리적 제약).

- ❌ 병합된 `V2.1.1__...sql` 내용 변경
- ✅ 새 파일 `V2.1.2__...sql`로 보완

## 4. Migration 파일 삭제 금지

- ❌ 병합된 마이그레이션 파일 삭제
- ✅ 잘못된 변경은 새 마이그레이션으로 되돌리기(revert)

---

## 5. Out-Of-Order 허용

`spring.flyway.out-of-order=true`. 낮은 버전이 뒤늦게 병합돼도 순서에 끼워넣어 **1회만** 적용된다(같은 파일이 두 번 실행되지 않는다).

```
V2.1.1 → V2.2.1 → V2.1.2 (뒤늦게 병합) → V2.3.1
```

## 6. DB 변경은 반드시 Migration으로

- ❌ DB에 직접 SQL 실행 후 종료
- ✅ 파일 생성 후 Flyway로 적용

## 7. 공용 테이블 변경 시 사전 공유

**module06 공용 테이블** = `company`, `member`, `team`, `sub_team`, `job_position`, `project`, `meeting`, `action` 등 테넌트/조직 핵심.
이들 변경 시 반드시 팀 채널에 사전 공유(out-of-order 환경에서 담당자 간 의존성 충돌 방어선).

```
[DB 변경 예정]
파일명: V2.2.1__add_last_login_at_to_member.sql
대상: member
변경 내용: last_login_at 컬럼 추가
영향 범위: 인증 / 마이페이지
```

---

## 8. 방어적 작성 원칙

### 8-1. ⚠ MySQL은 `ADD COLUMN IF NOT EXISTS`를 지원하지 않는다 (정정)

`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`는 **MariaDB 전용 문법**이다.
**MySQL 8에서는 문법 오류로 즉시 실패**한다. (이전 규칙 문서의 권장 예시는 MySQL에서 쓸 수 없다.)

MySQL에서의 원칙:
- **수동 DB 변경을 하지 않는다** — Flyway가 각 마이그레이션을 DB당 1회만 실행하므로, 수동 개입만 없으면 멱등성 방어 코드는 애초에 필요 없다.
- 로컬 DB가 꼬이면 방어 코드로 덮지 말고 **9번(재생성)**으로 초기화한다.
- 정말로 조건부 DDL이 필요하면 `INFORMATION_SCHEMA` 조회 + 동적 SQL(프로시저)로 감싼다. (남용 금지)

### 8-2. MySQL DDL 롤백 불가

MySQL은 `CREATE/ALTER/DROP` DDL이 **암묵적 커밋**을 일으킨다 → 한 파일에 DDL 여러 개면 중간 실패 시 앞부분이 롤백되지 않아 반쪽 적용으로 남는다.

- **1 Migration 파일 = 1 논리 변경.** 여러 테이블/여러 DDL은 파일을 나눈다.
- **예외**: `V1__init_schema.sql`(baseline)은 초기 전체 스키마라 다수 DDL을 한 파일에 담는다. baseline 이후에만 1파일-1변경을 강제한다.

---

## 9. 로컬 DB 오류 발생 시

1. 로컬 DB 삭제
2. Flyway 초기화
3. 애플리케이션 재실행
4. **`V1` → `V2.x`** 순서로 재적용

`flyway_schema_history` 직접 수정은 **금지**.

## 10. 작성 규칙

- 파일명: `V{버전}__{설명}.sql` (설명은 영문 snake_case)
  예) `V2.4.2__add_capacity_index_to_meeting_room.sql`
- 버전은 배정받은 담당자 영역을 지킨다.
- 뷰/시드/프로시저처럼 반복 적용 대상은 `R__` (Repeatable) 사용 검토.

## 11. 운영 환경 원칙

- 운영 DB 기존 Migration 수정 금지 → 문제는 새 Migration으로 보완(긴급 수정도 파일로 남긴다).
- 운영 반영 전 CI/스테이징에서 `flyway validate` + `flyway migrate` 확인.
- **머지 순서 = 배포 순서** 강제. out-of-order는 개발 편의용이며, 운영에선 공용 테이블 순서 의존성이 깨지지 않도록 병합/배포 순서 정합성을 유지한다.

---

## 최종 체크리스트

- [ ] Migration 파일 수정 금지 (checksum 불일치 → validate 실패)
- [ ] Migration 파일 삭제 금지
- [ ] DB 변경은 반드시 Migration으로
- [ ] 담당 버전 영역(V2.N.x)만 사용
- [ ] 공용 테이블 변경 시 사전 공유
- [ ] MySQL은 `IF NOT EXISTS` 컬럼 문법 불가 → 수동 DB 변경 자체를 금지
- [ ] 1파일 1논리변경 (baseline V1만 예외)
- [ ] baseline: baseline-on-migrate=true / baseline-version=1
- [ ] 충돌 시 로컬 DB 재생성 우선 (V1 → V2.x)
- [ ] 운영 반영 전 validate + migrate 검증
- [ ] 머지 순서 = 배포 순서 유지
