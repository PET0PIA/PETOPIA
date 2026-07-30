import type { ActivityItem, BoothPreview, CurrentUser, Fair } from "../types/domain";
import homeHeroPetfairImage from "../assets/home-hero-petfair.png";
import fairCardPetfairImage from "../assets/fair-card-petfair.png";

// API 연동 전 화면 검증용 상태입니다. 아래 값만 바꾸면 헤더와 내 활동 영역이 함께 변경됩니다.
export const currentUser: CurrentUser = {
  name: "김포피",
  role: "USER",
  businessStatus: "APPROVED",
  notificationCount: 3,
};

export const homeHero = {
  imageUrl: homeHeroPetfairImage,
  imageAlt: "티켓 부스 앞에 모인 강아지와 고양이 펫페어 일러스트",
};

export const upcomingFairs: Fair[] = [
  { id: "seoul-autumn", name: "2026 서울 펫페어", imageUrl: fairCardPetfairImage, status: "RESERVATION_OPEN", dates: "2026. 09. 18 - 09. 20", location: "서울 코엑스 C홀", exhibitorCount: 168, accent: "primary" },
  { id: "busan-walk", name: "부산 댕냥 산책 페스타", imageUrl: fairCardPetfairImage, status: "UPCOMING", dates: "2026. 10. 03 - 10. 04", location: "벡스코 제2전시장", exhibitorCount: 82, accent: "leaf" },
  { id: "daejeon-family", name: "대전 펫 패밀리데이", imageUrl: fairCardPetfairImage, status: "UPCOMING", dates: "2026. 11. 14 - 11. 15", location: "대전 컨벤션센터", exhibitorCount: 56, accent: "sun" },
];

export const boothPreviews: BoothPreview[] = [
  { id: "mild-meal", name: "마일드밀", category: "반려동물 식품", fairName: "2026 서울 펫페어", boothNumber: "A-12", initials: "MM", accent: "primary" },
  { id: "pawmade", name: "포메이드", category: "핸드메이드 용품", fairName: "2026 서울 펫페어", boothNumber: "B-07", initials: "PM", accent: "sun" },
  { id: "green-tail", name: "그린테일", category: "친환경 리빙", fairName: "부산 댕냥 산책 페스타", boothNumber: "C-19", initials: "GT", accent: "leaf" },
  { id: "happy-vet", name: "해피벳", category: "건강·케어", fairName: "2026 서울 펫페어", boothNumber: "A-28", initials: "HV", accent: "primary" },
];

export const personalActivities: ActivityItem[] = [
  { label: "예매한 티켓", value: "2건", description: "서울 펫페어 외 1건", tone: "primary" },
  { label: "다음 방문 일정", value: "9월 18일", description: "2026 서울 펫페어", tone: "sun" },
  { label: "읽지 않은 알림", value: "3건", description: "새 소식이 도착했어요", tone: "leaf" },
];

export const businessActivities: ActivityItem[] = [
  { label: "참가 신청", value: "1건", description: "서울 펫페어 심사 중", tone: "primary" },
  { label: "승인 대기", value: "1건", description: "참가 신청 결과를 기다려요", tone: "sun" },
  { label: "운영 예정 부스", value: "1개", description: "A-12 · 마일드밀", tone: "leaf" },
];
