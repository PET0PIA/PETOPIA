import { apiClient, type ApiEnvelope } from "./client";

/**
 * RECRUIT(모집공고)만 성격이 다르다. notice 테이블에 저장되지 않고, 목록을 만들 때 서버가
 * 행사별 모집공고에서 끌어와 붙이는 분류다 - 관리자 화면에서는 등록할 수 없다.
 */
export type NoticeCategory = "NOTICE" | "EVENT" | "GUIDE" | "RECRUIT";

export const NOTICE_CATEGORY_LABELS: Record<NoticeCategory, string> = {
  NOTICE: "공지",
  EVENT: "이벤트",
  GUIDE: "안내",
  RECRUIT: "모집공고",
};

/** 관리자가 직접 쓸 수 있는 분류(모집공고 제외). */
export const EDITABLE_NOTICE_CATEGORIES: NoticeCategory[] = ["NOTICE", "EVENT", "GUIDE"];

export interface NoticeListItem {
  /** 공지 id 또는 모집공고 id. 출처가 다르면 값이 겹칠 수 있어, 화면 key는 category와 조합해서 쓴다. */
  id: number;
  category: NoticeCategory;
  title: string;
  fairId: number | null;
  fairName: string | null;
  pinned: boolean;
  viewCount: number;
  createdAt: string;
  /** 서버가 정해주는 이동 주소. 공지는 /news/{id}, 모집공고는 기존 모집공고 화면. */
  linkPath: string;
}

export interface NoticeAttachment {
  attachmentId: number;
  fileUrl: string;
  originalName: string;
  /** byte. 화면 표기는 호출부에서 만든다. */
  fileSize: number;
}

export interface NoticeDetail {
  noticeId: number;
  category: NoticeCategory;
  title: string;
  /** HTML. 반드시 RichTextView(DOMPurify)를 거쳐 렌더해야 한다. */
  content: string;
  fairId: number | null;
  fairName: string | null;
  /**
   * 연결된 행사를 관람객이 열어볼 수 있는지. 아직 전체공개 안 된 행사·취소된 행사는 공개 상세가
   * 404라서, false면 행사 배지를 링크로 걸지 않는다. 판정은 서버가 한다(linkPath와 같은 이유).
   */
  fairPublic: boolean;
  pinned: boolean;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
  attachments: NoticeAttachment[];
}

/** 관리자 목록·단건. 공개 상세에 게시 여부(published)가 추가된 형태다. */
export interface AdminNotice extends NoticeDetail {
  published: boolean;
}

/**
 * 첨부 한 개. 기존 첨부를 그대로 둘 때는 attachmentId만, 새로 올린 파일은 나머지 3개를 채운다.
 * 저장 요청에 넣지 않은 기존 첨부는 삭제된다.
 */
export interface NoticeAttachmentInput {
  attachmentId?: number;
  objectKey?: string;
  originalName?: string;
  fileSize?: number;
}

export interface NoticeSaveInput {
  category: NoticeCategory;
  title: string;
  content: string;
  fairId?: number | null;
  pinned: boolean;
  /** 생략하면 등록 시 바로 게시, 수정 시 현재 상태 유지. */
  published?: boolean;
  attachments?: NoticeAttachmentInput[];
}

// 공개 - 소식 목록 (공지 + 모집중인 모집공고)
export async function getNotices(category?: NoticeCategory): Promise<NoticeListItem[]> {
  const query = category ? `?category=${category}` : "";
  const response = await apiClient.get<ApiEnvelope<NoticeListItem[]>>(`/api/notices${query}`);
  return response.data;
}

// 공개 - 공지 상세 (조회수가 올라간다)
export async function getNotice(noticeId: number): Promise<NoticeDetail> {
  const response = await apiClient.get<ApiEnvelope<NoticeDetail>>(`/api/notices/${noticeId}`);
  return response.data;
}

// 관리자 - 전체 목록 (비공개 포함)
export async function getAdminNotices(): Promise<AdminNotice[]> {
  const response = await apiClient.get<ApiEnvelope<AdminNotice[]>>("/api/admin/notices");
  return response.data;
}

// 관리자 - 등록
export async function createNotice(payload: NoticeSaveInput): Promise<AdminNotice> {
  const response = await apiClient.post<ApiEnvelope<AdminNotice>>("/api/admin/notices", payload);
  return response.data;
}

// 관리자 - 수정 (폼 전체를 덮어쓴다)
export async function updateNotice(noticeId: number, payload: NoticeSaveInput): Promise<AdminNotice> {
  const response = await apiClient.put<ApiEnvelope<AdminNotice>>(`/api/admin/notices/${noticeId}`, payload);
  return response.data;
}

// 관리자 - 삭제 (첨부도 함께 지워진다)
export async function deleteNotice(noticeId: number): Promise<void> {
  await apiClient.delete(`/api/admin/notices/${noticeId}`);
}

// 관리자 - 게시/비공개 토글
export async function setNoticePublished(noticeId: number, isPublished: boolean): Promise<void> {
  await apiClient.patch(`/api/admin/notices/${noticeId}/publish?isPublished=${isPublished}`);
}

// 관리자 - 상단 고정 토글
export async function setNoticePinned(noticeId: number, isPinned: boolean): Promise<void> {
  await apiClient.patch(`/api/admin/notices/${noticeId}/pin?isPinned=${isPinned}`);
}

/**
 * 관리자 - 본문 에디터에 넣을 이미지 확정. 임시 업로드된 objectKey를 보내면 바로 <img src>에
 * 쓸 수 있는 공개 URL을 돌려준다(배너·팝업과 달리 저장 전에 확정한다 - 에디터가 지금 당장
 * 보여줄 주소를 필요로 하기 때문).
 */
export async function confirmNoticeImage(objectKey: string): Promise<string> {
  const response = await apiClient.post<ApiEnvelope<{ url: string }>>("/api/admin/notices/images", { objectKey });
  return response.data.url;
}
