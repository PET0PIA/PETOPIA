# PETOPIA AI UI 작업 규칙

PETOPIA의 새 화면은 한 서비스처럼 보이도록 아래 기준을 지켜야 합니다. 작업 범위 밖의 파일은 변경하지 않습니다.

## 작업 전 반드시 읽기

1. `src/styles/theme.css` — 색상·모서리·공통 표면 토큰
2. `src/layouts/` — 사용자/박람회 관리자/전체 관리자 레이아웃
3. `src/components/common/`, `src/components/ui/` — 공통 컴포넌트
4. `src/config/navigation.ts` — 메뉴와 경로 설정
5. 구현할 화면과 가장 유사한 기존 페이지 및 해당 도메인 mock 파일

## 디자인 규칙

- 임의의 HEX 색상을 추가하지 말고 `theme.css`의 공통 색상 토큰과 Tailwind 토큰만 사용합니다.
- 카드 radius는 `rounded-card`, 버튼과 입력은 `rounded-button`으로 통일합니다.
- 과도한 그림자와 그라데이션은 사용하지 않습니다. 카드는 `surface`를 우선 사용합니다.
- 이모지를 아이콘 대신 사용하지 않고 Lucide React 아이콘을 사용합니다.
- 본문은 `PageContainer`의 최대 너비와 모바일 `px-4` 여백을 따릅니다.
- 빨강은 핵심 행동·선택, 노랑은 안내·예정, 초록은 승인·완료·정상 상태에만 씁니다.
- 빨강·노랑·초록을 한 요소에 동시에 배치하지 않습니다.
- 테이블과 폼은 장식보다 읽기 쉬운 행간, 레이블, 대비를 우선합니다.
- 귀여운 분위기는 색상, 둥근 모서리, 제한적인 작은 장식으로만 표현합니다.

## 컴포넌트 규칙

- 기존 공통 컴포넌트를 먼저 사용합니다. `Button`, `Input`, `Card`, `Table`, `Dialog`, `Select`를 페이지마다 새로 만들지 않습니다.
- 페이지 제목은 `PageHeader`, 섹션 제목은 `SectionHeader`를 사용합니다.
- 행사 상태는 `StatusBadge`, 데이터 없음은 `EmptyState`를 사용합니다.
- 기존 컴포넌트로 해결되지 않으면 공통 컴포넌트를 바로 수정하지 말고 먼저 도메인 컴포넌트를 만듭니다.
- 공통 컴포넌트 변경이 필요하면 변경 이유와 영향을 작업 결과에 기록합니다.
- API URL은 컴포넌트에 직접 작성하지 않고, 추후 API 모듈로 분리합니다.

## 수정 제한

별도 요청이 없다면 아래 디렉터리는 수정하지 않습니다.

```text
src/styles
src/layouts
src/components/ui
src/components/layout
src/config
```

## 도메인별 AI 작업 원칙

- 요청받은 도메인 범위만 구현하고 다른 도메인 페이지는 수정하지 않습니다.
- 공통 헤더와 사이드바를 임의로 변경하지 않습니다.
- API가 없다면 mock 데이터를 별도 파일에 둡니다.
- 서버 상태(loading/error/empty)와 화면 상태를 구분할 수 있도록 설계합니다.
- 접근성을 지킵니다: 의미 있는 버튼/링크, 아이콘 버튼의 `aria-label`, 키보드 focus 상태, 이미지 `alt`.
- 작업 후 생성·수정 파일, mock 변경 위치, lint/build 결과를 보고합니다.
- 기존 프로젝트의 lint와 production build를 통과시킵니다.
