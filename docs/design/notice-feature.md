# 공지사항(소식·이벤트) 기능 가이드

작성 2026-08-19. 아직 코드는 하나도 건드리지 않았고, **무엇을 어떤 순서로 만들지** 정리한 문서다.
이번 범위는 **공지 CRUD + 관람객 소식 화면까지**. 예약자 문자(SMS) 발송은 통째로 보류.

---

## 1. 지금 홈 화면은 어떻게 돼 있나

홈(`/`)은 `HomePage.tsx` 한 줄짜리 파일이고, 섹션 컴포넌트를 위에서부터 쌓는다.

```
PopupModal            팝업          ← 이미 실제 API (/api/popups/active)
HeroSection           배너 슬라이드  ← 이미 실제 API (/api/banners/active)
HomeNoticeMarquee     검정 띠        ← ⚠ mock (문자열 5개 하드코딩)
QuickMenuSection      바로가기
UpcomingFairSection   다가오는 행사  ← 실제 API
BoothPreviewSection   부스 미리보기
FairReviewSection     후기
PetopiaNewsSection    "PETOPIA 소식" ← ⚠ mock (공지 6건 하드코딩)
```

**공지와 관련된 자리는 4곳이고, 그중 2곳은 화면이 이미 다 그려져 있다.**

| 위치 | 파일 | 현재 상태 |
|---|---|---|
| 홈 검정 마퀴 띠 | [HomeNoticeMarquee.tsx](frontend/src/components/home/HomeNoticeMarquee.tsx) | `mocks/home.ts`의 `homeNotices` 문자열 배열이 흐른다 |
| 홈 "PETOPIA 소식" 섹션 | [PetopiaNewsSection.tsx](frontend/src/components/home/PetopiaNewsSection.tsx) | `petopiaNews` mock 6건. 카테고리 필터(전체/공지/이벤트/안내)까지 이미 동작. 단 **행을 눌러도 아무 일도 안 남** |
| 헤더 "소식·이벤트" → `/news` | [AppRouter.tsx:190](frontend/src/router/AppRouter.tsx:190) | `NotImplementedPage`("미구현" 글자만) |
| 관리자 "공지사항 관리" → `/admin/notices` | [AppRouter.tsx:286](frontend/src/router/AppRouter.tsx:286) | `NotImplementedPage` |

즉 **화면 뼈대와 메뉴는 이미 뚫려 있고, 백엔드 데이터와 관리자 CRUD만 없다.**

### 목표 데이터 흐름

```
[최고 관리자] /admin/notices 에서 공지 등록·수정·삭제·게시토글
        │  POST/PUT/DELETE /api/admin/notices
        ▼
    notice 테이블 ──┐
                    ├─► GET /api/notices  (게시된 것만 + 모집공고 자동 병합)
  recruit_notice ───┘        │
  (기존 테이블, 행사별 모집공고) ├──► 홈 마퀴 띠     (고정 공지 제목이 흐름)
                              ├──► 홈 소식 섹션   (최신 6건, 클릭 → 상세)
                              ├──► /news          (전체 목록 + 카테고리 필터)
                              └──► /news/:id      (공지 상세) / 모집공고는 기존 상세로
```

---

## 2. 따라 만들 청사진: 팝업(popup) 도메인

새로 발명할 게 거의 없다. **팝업 도메인이 구조·네이밍·화면까지 그대로 쓸 수 있는 본보기**다.

```
백엔드  src/main/java/com/ms/petopia/api/popup/
          controller/PopupController.java        공개 GET
          controller/AdminPopupController.java   /api/admin/popups CRUD + toggle
          domain/Popup.java  dto/request  dto/response  mapper  service
        src/main/resources/mapper/popup/PopupMapper.xml
        src/main/resources/db/migration/V16__create_banner_and_popup.sql

프론트  frontend/src/api/popup.ts
        frontend/src/pages/admin/AdminPopupsPage.tsx   (447줄, 목록 표 + Dialog 폼)
```

공지도 같은 모양으로 `api/notice/` 패키지를 새로 판다.

### 조사 결과: 모집공고쪽에 에디터는 없었다

[RecruitNoticeFormPage.tsx:185](frontend/src/pages/recruit-notice/RecruitNoticeFormPage.tsx:185)는 그냥 `Textarea`이고, 상세는 `whitespace-pre-wrap`으로 줄바꿈만 살려 뿌린다. 이미지는 본문과 별개로 **대표 1장**을 위에 붙일 뿐이다. `package.json`에도 에디터 라이브러리가 없다.
반면 **첨부파일 부품은 이미 완성돼 있다** — `AttachmentUploadField`(PDF·DOCX·XLSX·PPTX, 50MB)와 `api/files.ts`의 S3 presigned 업로드 흐름. 공지에서는 그대로 재사용한다.

---

## 3. 데이터 모델

새 Flyway 파일 **`V40__create_notice.sql`**. 저장소의 최신은 V38이지만 **V39는 다른 팀원이 작업 중**이라 한 칸 건너뛴다. 이미 적용된 파일은 절대 수정하지 않는다.

### `notice`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `notice_id` | BIGINT PK AI | |
| `category` | VARCHAR(20) NOT NULL | `NOTICE`(공지) / `EVENT`(이벤트) / `GUIDE`(안내) — **모집공고는 이 테이블에 저장하지 않는다**(§4 참고) |
| `title` | VARCHAR(200) NOT NULL | 목록·마퀴에 노출 |
| `content` | MEDIUMTEXT NOT NULL | 에디터가 만든 **HTML**. 이미지가 본문 안에 `<img>`로 섞여 들어가서 TEXT보다 넉넉하게 잡는다 |
| `fair_id` | BIGINT NULL FK fairs | 이 공지가 특정 행사에 대한 것이면 연결(선택). 목록·상세에 행사 배지를 달고, 나중 문자 발송의 대상 기준이 된다 |
| `is_published` | TINYINT(1) NOT NULL DEFAULT 1 | 게시 여부. 팝업의 `is_active`와 같은 역할 |
| `is_pinned` | TINYINT(1) NOT NULL DEFAULT 0 | 상단 고정. 마퀴 띠 재료로도 쓴다 |
| `view_count` | INT NOT NULL DEFAULT 0 | 조회수 |
| `created_by` | BIGINT NOT NULL FK users | 등록 관리자 |
| `created_at` / `updated_at` | DATETIME | 팝업 테이블과 동일 규약 |

인덱스: `(is_published, is_pinned, created_at DESC)` 하나면 공개 목록 조회에 충분하다.

### `notice_attachment` (첨부파일)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `attachment_id` | BIGINT PK AI | |
| `notice_id` | BIGINT NOT NULL FK notice **ON DELETE CASCADE** | 공지를 지우면 첨부 행도 같이 지워진다 |
| `file_url` | VARCHAR(500) NOT NULL | 확정된 공개 URL (배너의 `image_key`와 같은 방식) |
| `original_name` | VARCHAR(255) NOT NULL | 화면에 보여줄 원본 파일명 |
| `file_size` | BIGINT NOT NULL | "2.4MB"처럼 표시 |
| `sort_order` | INT NOT NULL DEFAULT 0 | |

한 공지당 첨부는 **최대 5개**로 제한한다(서버에서 검증).

> `category`를 DB ENUM이 아니라 VARCHAR + Java enum으로 두는 이유: 나중에 카테고리를 늘릴 때 마이그레이션 없이 코드만 고치면 된다.

---

## 4. API 설계

### 공개 (비로그인 포함 누구나)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/notices` | 게시된 공지 + **모집공고 병합** 목록. `?category=NOTICE\|EVENT\|GUIDE\|RECRUIT` 선택 |
| GET | `/api/notices/{noticeId}` | 공지 상세(첨부 목록 포함). 비게시 글이면 404. 조회수 +1 |

**모집공고 병합이 이번 설계의 핵심이다.** 행사별 모집공고는 이미 `recruit_notice` 테이블(행사당 1건, 행사 담당자가 작성)과 `/fairs/:fairId/recruit-notice` 상세 화면으로 존재한다. 그래서 공지 테이블에 베끼지 않고, **목록을 만들 때 서버가 두 곳에서 읽어 하나로 합친다.**

목록 응답 한 건은 이렇게 생긴다:

```json
{
  "category": "RECRUIT",
  "title": "2026 서울 펫페어 참가업체 모집",
  "fairId": 12,
  "fairName": "2026 서울 펫페어",
  "createdAt": "2026-08-10T10:00:00",
  "pinned": false,
  "viewCount": 0,
  "linkPath": "/fairs/12/recruit-notice"
}
```

- `linkPath`를 서버가 정해서 내려준다 → 프론트는 출처를 따질 필요 없이 그냥 그 주소로 보내면 된다. 일반 공지는 `/news/{noticeId}`.
- 두 테이블의 id가 겹칠 수 있으니 리스트의 React `key`는 `${category}-${id}`로 만든다.
- 정렬: 고정(`pinned`) 먼저 → 최신순. 모집공고는 고정 대상이 아니다.
- **마감 지난 모집공고는 목록에서 뺀다**(`recruit_deadline < NOW()` 제외). 소식 목록은 "지금 볼 만한 것" 위주라서.
- 페이징은 처음엔 안 넣는다. 배너·팝업도 전체를 한 번에 내려주고, `/news`는 "더 보기" 버튼으로 잘라 보여주면 충분하다.
- `SecurityConfig`에 규칙을 **추가할 필요 없다.** 맨 아래 `anyRequest().permitAll()`이 있어 공개 API는 그냥 열린다.

### 관리자 (SUPER_ADMIN)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/admin/notices` | 비공개 포함 전체(모집공고는 안 섞음 — 최고 관리자가 손댈 글이 아니다) |
| GET | `/api/admin/notices/{id}` | 단건 |
| POST | `/api/admin/notices` | 등록. `@AuthenticationPrincipal Long callerId` → `created_by` |
| PUT | `/api/admin/notices/{id}` | 수정 |
| DELETE | `/api/admin/notices/{id}` | 삭제(첨부는 CASCADE) |
| PATCH | `/api/admin/notices/{id}/publish?isPublished=` | 게시/비공개 토글 |
| PATCH | `/api/admin/notices/{id}/pin?isPinned=` | 상단 고정 토글 |
| POST | `/api/admin/notices/images` | **에디터 전용.** tmp objectKey를 받아 즉시 확정하고 공개 URL을 돌려준다 |

`/api/admin/**`는 [SecurityConfig:65](src/main/java/com/ms/petopia/global/security/SecurityConfig.java:65)에서 이미 관리자 역할로 잠겨 있어 **인가 설정을 새로 안 해도 된다.**

에러코드 2줄 추가(AD001 배너, AD002 팝업 다음):
`NOTICE_NOT_FOUND(NOT_FOUND, "AD003", …)`, `NOTICE_ATTACHMENT_LIMIT(BAD_REQUEST, "AD004", "첨부파일은 최대 5개까지 등록할 수 있습니다.")`

### 등록/수정 요청 본문

```json
{
  "category": "NOTICE",
  "title": "…",
  "content": "<p>에디터가 만든 HTML</p><img src=\"https://…\">",
  "fairId": 12,
  "isPinned": false,
  "attachments": [
    { "objectKey": "tmp/document/…pdf", "originalName": "참가안내.pdf", "size": 254112 }
  ]
}
```

서버는 각 `objectKey`에 `storageService.confirm()` → `toPublicUrl()`을 돌려 `notice_attachment`에 넣는다. 배너·팝업 이미지가 이미 이 방식이다.

---

## 5. 본문 에디터 (Tiptap) — 새로 들어오는 부분

프로젝트에 처음 들어오는 라이브러리라 따로 적어둔다.

**추가할 패키지**: `@tiptap/react`, `@tiptap/pm`, `@tiptap/starter-kit`, `@tiptap/extension-image`, `dompurify`
(React 19.2.8을 쓰고 있고 Tiptap v3가 지원한다.)

**툴바는 최소한만**: 굵게 · 기울임 · 제목 · 목록 · 링크 · 이미지 삽입. 표·색상 같은 건 넣지 않는다.

**이미지가 본문에 들어가는 순서** — 여기가 유일하게 까다로운 곳이다:

1. 관리자가 에디터에서 이미지 버튼을 누른다
2. 기존 `api/files.ts`의 presigned 업로드로 S3 **tmp**에 올린다 → `objectKey`를 받는다
3. `POST /api/admin/notices/images`에 그 키를 보내 **공개 URL**을 받는다
4. 받은 URL을 에디터에 `<img src>`로 꽂는다

3번 단계가 필요한 이유: tmp에 올라간 파일은 아직 공개 URL이 없는데, 에디터는 지금 당장 보여줄 주소가 필요하다. 배너처럼 "저장할 때 한꺼번에 확정"하는 방식은 본문 HTML 안에 섞인 이미지에는 쓰기 어렵다.
부작용: 이미지를 넣고 저장하지 않고 창을 닫으면 S3에 파일이 남는다. 관리자만 쓰는 화면이라 이번엔 그대로 두고, 신경 쓰이면 나중에 "안 쓰는 파일 청소" 배치를 만든다.

**보안(중요)**: 본문이 HTML이므로 `/news` 상세에서 그대로 뿌리면 안 된다. 반드시 `DOMPurify.sanitize()`를 거친 뒤 렌더하고, 허용 태그를 `p, br, strong, em, u, h2, h3, ul, ol, li, a, img, blockquote` 정도로 좁힌다. 관리자만 쓰는 필드라 서버측 정화(jsoup)까지는 이번에 넣지 않는다.

---

## 6. 화면 설계

### (1) 관리자 — `/admin/notices`
`AdminPopupsPage.tsx`를 본떠서: "새 공지 등록" 버튼 + 표(카테고리·제목·행사·게시상태·고정·조회수·작성일·수정/삭제) + `Dialog` 안에 폼.
폼: 카테고리 `Select` / 제목 `Input` / **본문 에디터** / 행사 연결 `Select`(선택, 비워도 됨) / 상단 고정 체크박스 / 첨부파일(최대 5개).

### (2) 관람객 목록 — `/news`
`NotImplementedPage`를 실제 페이지로 교체. 홈 소식 섹션과 **같은 pill 필터 + 같은 행 디자인**을 쓰되 탭이 5개가 된다: `전체 · 공지 · 이벤트 · 안내 · 모집공고`.
고정 공지는 맨 위에 📌 배지, 행사 연결된 글은 행사명 배지, 모집공고는 눌렀을 때 기존 상세로 이동.

### (3) 관람객 상세 — `/news/:noticeId`
제목 / 카테고리·행사 배지 / 작성일 / 조회수 / **정화한 HTML 본문** / 첨부파일 목록(파일명 + 크기, 클릭 시 다운로드) / 목록으로 버튼.

### (4) 홈 소식 섹션 — 기존 컴포넌트 수정
`petopiaNews` mock → `GET /api/notices` 최신 6건. 행을 `Link`(서버가 준 `linkPath`)로 감싸고, 필터에 "모집공고" 탭을 추가하고, 하단에 "전체 보기 → /news" 링크를 넣는다.

### (5) 홈 마퀴 띠 — 기존 컴포넌트 수정
`homeNotices` mock → **고정(`is_pinned`) 공지 제목**을 흘린다. 고정 공지가 없으면 최신 5건으로 채우고, 그마저 없으면 띠 자체를 렌더하지 않는다(빈 검정 띠 방지). 제목 클릭 시 상세로 이동.

> 홈에서 두 컴포넌트가 각자 API를 부르면 같은 목록을 두 번 요청한다. 목록이 작으니 처음엔 그대로 두고(코드가 단순함), 신경 쓰이면 `HomePage`에서 한 번 받아 props로 내려준다.

---

## 7. 확정된 결정

| # | 정한 것 | 결론 |
|---|---|---|
| 1 | 카테고리 | 공지 · 이벤트 · 안내 **+ 모집공고**(기존 `recruit_notice`에서 자동으로 끌어옴, 관리자가 따로 쓰지 않음) |
| 2 | 본문 편집 | **Tiptap 에디터** 도입. 본문은 HTML, 렌더 시 DOMPurify 필수 |
| 3 | 조회수 | 넣는다 (`view_count`, 상세 조회 시 +1) |
| 4 | 이미지·첨부 | 이미지는 **본문 안에 삽입**, 첨부파일은 별도 목록(최대 5개, 기존 `AttachmentUploadField` 재사용) |
| 5 | 행사 연결(`fair_id`) | **쓴다.** 폼에서 선택 입력, 목록·상세에 행사 배지. 나중 문자 발송의 대상 기준도 된다 |
| 6 | 노출 기간 | 안 넣는다. 게시/비공개 토글만 (공지는 계속 남는 글) |

---

## 8. 작업 순서 (한 단계씩 · 각 단계 끝에 확인)

**1단계 — DB**
`V40__create_notice.sql`(notice + notice_attachment) 작성 → `./gradlew bootRun`으로 마이그레이션 통과만 확인.

**2단계 — 백엔드 공개 API**
`domain/Notice`, `NoticeMapper(+XML)`, `NoticeService`, `NoticeController`, `ErrorCode` 2줄.
모집공고 병합·조회수·첨부 조회까지 여기서 끝낸다.
확인: 손으로 2~3건 INSERT 후 `curl localhost:8080/api/notices` — 모집공고가 섞여 나오는지.

**3단계 — 백엔드 관리자 API**
`AdminNoticeController` + CRUD·토글 2종·이미지 확정 엔드포인트, 첨부 confirm 처리.
확인: 관리자 토큰으로 등록 → 공개 목록에 뜨는지, 비공개로 바꾸면 사라지는지.

**4단계 — 에디터 붙이기**
Tiptap 설치 + `components/ui/RichTextEditor.tsx` 신설(툴바 + 이미지 업로드 훅). **이 단계만 따로 떼어 확인한다** — 새 라이브러리라 여기서 시간이 제일 많이 든다.

**5단계 — 관리자 화면**
`api/notice.ts` + `AdminNoticesPage.tsx`, 라우터에서 `NotImplementedPage` 교체, [AppRouter.tsx:124](frontend/src/router/AppRouter.tsx:124)의 fallback 제외 목록에서 `/admin/notices` 줄 삭제.
확인: 브라우저에서 등록·수정·삭제·게시토글·이미지 삽입·첨부 한 바퀴.

**6단계 — 관람객 화면**
`/news` 목록 + `/news/:id` 상세 신설(DOMPurify 렌더).

**7단계 — 홈 연결**
`PetopiaNewsSection`, `HomeNoticeMarquee`를 실제 API로 교체하고 `mocks/home.ts`에서 `homeNotices`·`petopiaNews`·`NewsCategory` 삭제.
확인: `npm run build` + `npm run lint`, 홈에서 실제 공지가 보이는지.

3단계 끝, 4단계 끝, 5단계 끝, 7단계 끝에서 각각 멈추고 같이 확인하는 걸 추천한다.

---

## 9. 이번에 안 하는 것

- 예약자 문자(SMS) 발송 — SMS 인프라가 없고 발신번호 사전등록·광고성 문자 규제가 걸린다. 별도 단계. (`fair_id`를 채워두므로 그때 DB는 안 뜯어도 된다)
- 공지 댓글, 공지 검색, 예약 발행(지정 시각 자동 게시).
- S3 고아 파일 청소 배치 (§5의 부작용 참고).
- 알림(`notification`) 도메인 연동 — 공지 등록 시 사용자에게 알림 보내기는 다음 후보.
