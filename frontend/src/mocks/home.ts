import type { ActivityItem, BoothPreview, CurrentUser, Fair } from "../types/domain";
import fairPoster1 from "../assets/posters/poster-1.png";
import fairPoster2 from "../assets/posters/poster-2.png";
import fairPoster3 from "../assets/posters/poster-3.png";
import fairPoster4 from "../assets/posters/poster-4.png";
import fairPoster5 from "../assets/posters/poster-5.png";
import booth1 from "../assets/boothPosters/booth-1.png";
import booth2 from "../assets/boothPosters/booth-2.png";
import booth3 from "../assets/boothPosters/booth-3.png";
import booth4 from "../assets/boothPosters/booth-4.png";
import booth5 from "../assets/boothPosters/booth-5.png";
import booth6 from "../assets/boothPosters/booth-6.png";
import booth7 from "../assets/boothPosters/booth-7.png";
import booth8 from "../assets/boothPosters/booth-8.png";
import booth9 from "../assets/boothPosters/booth-9.png";
import booth10 from "../assets/boothPosters/booth-10.png";
import reviewSpa from "../assets/fairReview/dogwithspa.jpg";
import reviewHappy from "../assets/fairReview/happydog.jpg";
import reviewPlay from "../assets/fairReview/playdog.jpg";

// API 연동 전 화면 검증용 상태입니다. 아래 값만 바꾸면 헤더와 내 활동 영역이 함께 변경됩니다.
export const currentUser: CurrentUser = {
  name: "김포피",
  role: "USER",
  businessStatus: "APPROVED",
  notificationCount: 3,
};

interface HeroSlide {
  eyebrow?: string;
  title: string;
  subtitle: string;
  cta: { label: string; to: string };
  cta2?: { label: string; to: string };
  image: string;
  bg: string; // 각 포스터와 어울리는 배경색(hex)
}

// 배너 슬라이드: 포스터가 3초마다 자동 전환된다. 각 장은 행사 홍보 포스터 + 어울리는 배경색.
export const heroSlides: HeroSlide[] = [
  { eyebrow: "예매 오픈", title: "2026 서울 펫페어\n지금 예매하세요", subtitle: "2026. 09. 18 - 09. 20 · 서울 코엑스 C홀", cta: { label: "예매하러 가기", to: "/fairs/upcoming?fair=seoul-autumn" }, cta2: { label: "티켓 예매하기", to: "/tickets" }, image: fairPoster1, bg: "#FBD9BD" },
  { eyebrow: "곧 만나요", title: "부산 댕냥 산책 페스타\n반려견과 함께 걷다", subtitle: "2026. 10. 03 - 10. 04 · 벡스코 제2전시장", cta: { label: "자세히 보기", to: "/fairs/upcoming?fair=busan-walk" }, image: fairPoster2, bg: "#DCEBC6" },
  { eyebrow: "가족 나들이", title: "대전 펫 패밀리데이\n온 가족이 즐기는 하루", subtitle: "2026. 11. 14 - 11. 15 · 대전 컨벤션센터", cta: { label: "자세히 보기", to: "/fairs/upcoming?fair=daejeon-family" }, image: fairPoster3, bg: "#BEE3DC" },
  { eyebrow: "예매 오픈", title: "인천 반려동물 박람회\n지금 예매하세요", subtitle: "2026. 11. 28 - 11. 29 · 송도 컨벤시아", cta: { label: "예매하러 가기", to: "/fairs/upcoming?fair=incheon-expo" }, image: fairPoster4, bg: "#F7D2E1" },
  { eyebrow: "연말 페스티벌", title: "광주 펫 페스티벌\n한 해의 마지막을 함께", subtitle: "2026. 12. 12 - 12. 13 · 김대중컨벤션센터", cta: { label: "자세히 보기", to: "/fairs/upcoming?fair=gwangju-fest" }, image: fairPoster5, bg: "#F5E1A3" },
];

// 배너 아래 검정 마퀴 띠에 흐르는 공지 문구
export const homeNotices: string[] = [
  "제2회 대구 펫 페스티벌이 성황리에 종료되었습니다. 함께해주셔서 감사합니다",
  "2026 서울 펫페어 얼리버드 티켓 오픈",
  "부산 댕냥 산책 페스타 참가 부스 모집 중",
  "인천 반려동물 박람회 참가 신청 마감 임박",
  "대전 펫 패밀리데이 반려동물 동반 입장 안내",
];

export const upcomingFairs: Fair[] = [
  { id: "seoul-autumn", name: "2026 서울 펫페어", imageUrl: fairPoster1, status: "RESERVATION_OPEN", dates: "2026. 09. 18 - 09. 20", location: "서울 코엑스 C홀", exhibitorCount: 168, accent: "primary" },
  { id: "busan-walk", name: "부산 댕냥 산책 페스타", imageUrl: fairPoster2, status: "UPCOMING", dates: "2026. 10. 03 - 10. 04", location: "벡스코 제2전시장", exhibitorCount: 82, accent: "leaf" },
  { id: "daejeon-family", name: "대전 펫 패밀리데이", imageUrl: fairPoster3, status: "UPCOMING", dates: "2026. 11. 14 - 11. 15", location: "대전 컨벤션센터", exhibitorCount: 56, accent: "sun" },
  { id: "incheon-expo", name: "인천 반려동물 박람회", imageUrl: fairPoster4, status: "RESERVATION_OPEN", dates: "2026. 11. 28 - 11. 29", location: "송도 컨벤시아", exhibitorCount: 121, accent: "primary" },
  { id: "gwangju-fest", name: "광주 펫 페스티벌", imageUrl: fairPoster5, status: "UPCOMING", dates: "2026. 12. 12 - 12. 13", location: "김대중컨벤션센터", exhibitorCount: 74, accent: "leaf" },
];

export const boothPreviews: BoothPreview[] = [
  { id: "mild-meal", name: "마일드밀", category: "반려동물 식품", fairName: "2026 서울 펫페어", boothNumber: "A-12", imageUrl: booth1, initials: "MM", accent: "primary" },
  { id: "pawmade", name: "포메이드", category: "핸드메이드 용품", fairName: "2026 서울 펫페어", boothNumber: "B-07", imageUrl: booth2, initials: "PM", accent: "sun" },
  { id: "green-tail", name: "그린테일", category: "친환경 리빙", fairName: "부산 댕냥 산책 페스타", boothNumber: "C-19", imageUrl: booth3, initials: "GT", accent: "leaf" },
  { id: "happy-vet", name: "해피벳", category: "건강·케어", fairName: "2026 서울 펫페어", boothNumber: "A-28", imageUrl: booth4, initials: "HV", accent: "primary" },
  { id: "nyang-house", name: "냥이하우스", category: "고양이 용품", fairName: "2026 서울 펫페어", boothNumber: "B-15", imageUrl: booth5, initials: "NH", accent: "sun" },
  { id: "wal-bakery", name: "왈왈베이커리", category: "수제 간식", fairName: "2026 서울 펫페어", boothNumber: "C-03", imageUrl: booth6, initials: "WB", accent: "leaf" },
  { id: "pet-style", name: "펫스타일", category: "반려동물 패션", fairName: "부산 댕냥 산책 페스타", boothNumber: "A-45", imageUrl: booth7, initials: "PS", accent: "primary" },
  { id: "dog-knit", name: "도그니트", category: "니트·의류", fairName: "2026 서울 펫페어", boothNumber: "B-22", imageUrl: booth8, initials: "DK", accent: "sun" },
  { id: "puppy-toy", name: "퍼피토이", category: "장난감", fairName: "대전 펫 패밀리데이", boothNumber: "C-11", imageUrl: booth9, initials: "PT", accent: "leaf" },
  { id: "cat-grass", name: "캣그라스", category: "반려식물", fairName: "2026 서울 펫페어", boothNumber: "A-09", imageUrl: booth10, initials: "CG", accent: "primary" },
];

// 박람회 리뷰(ocreo 페어스토리 대응): 다녀온 반려인 후기 카드. image는 카드 상단 대표 사진.
interface FairReview {
  id: string;
  author: string;
  rating: number;
  title: string;
  content: string;
  fairName: string;
  image: string;
}

export const fairReviews: FairReview[] = [
  { id: "dogwithspa", author: "코코맘", rating: 5, title: "이번 광주 펫페어 v.7을 다녀오며", content: "강아지 팩을 샀는데 스파할 때 사용하니까 좋은 것 같아요. 눈물도 덜 나고 확실히 순한 게 느껴져요. 다음에도 또 방문하고 싶은 박람회였어요!", fairName: "광주 펫페어 v.7", image: reviewSpa },
  { id: "happydog", author: "구름이집사", rating: 5, title: "울산 강아지 축제 후기!", content: "실외에 울타리가 쳐져 있어서 우리 강아지가 신나게 뛰어놀았어요! 운영 시설도 편하게 잘 되어 있어서 하루 종일 편하게 즐기다 왔습니다.", fairName: "울산 강아지 축제", image: reviewHappy },
  { id: "playdog", author: "보리아빠", rating: 5, title: "일산 펫페어 다녀온 후기", content: "저희 아이는 휴지 뜯는 걸 좋아하는데 마침 딱 좋은 장난감을 팔더라구요. 지금도 너무 잘 놀아요. 내부도 깔끔하고 질서 있게 정돈되어 있어 둘러보기 편했어요.", fairName: "일산 펫페어", image: reviewPlay },
];

// PETOPIA 소식(ocreo 소식 섹션 대응): 카테고리 pill 필터 + 공지 리스트.
export type NewsCategory = "공지" | "이벤트" | "안내";
interface NewsItem {
  id: string;
  category: NewsCategory;
  title: string;
  date: string;
}

export const petopiaNews: NewsItem[] = [
  { id: "news-1", category: "공지", title: "2026 서울 펫페어 예매가 오픈되었습니다", date: "2026.08.05" },
  { id: "news-2", category: "이벤트", title: "얼리버드 티켓 20% 할인 이벤트 (~8/20)", date: "2026.08.02" },
  { id: "news-3", category: "안내", title: "반려동물 동반 입장 시 필수 준비물 안내", date: "2026.07.28" },
  { id: "news-4", category: "공지", title: "부산 댕냥 산책 페스타 부스 참가 신청 접수", date: "2026.07.22" },
  { id: "news-5", category: "이벤트", title: "SNS 후기 인증하고 한정 굿즈 받아가세요", date: "2026.07.15" },
  { id: "news-6", category: "안내", title: "현장 QR 입장 방법 및 유의사항 안내", date: "2026.07.08" },
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
