export type BusinessStatus = "NOT_REGISTERED" | "PENDING" | "APPROVED";
export type FairStatus = "UPCOMING" | "RESERVATION_OPEN" | "IN_PROGRESS" | "ENDED";
export type UserRole = "GUEST" | "USER" | "BUSINESS";

export interface CurrentUser {
  name: string;
  role: UserRole;
  businessStatus: BusinessStatus;
  notificationCount: number;
}

export interface Fair {
  id: string;
  name: string;
  imageUrl: string;
  status: FairStatus;
  dates: string;
  location: string;
  exhibitorCount: number;
  accent: "sun" | "leaf" | "primary";
}

export interface BoothPreview {
  id: string;
  name: string;
  category: string;
  fairName: string;
  boothNumber: string;
  imageUrl: string;
  initials: string;
  accent: "sun" | "leaf" | "primary";
}

export interface ActivityItem {
  label: string;
  value: string;
  description: string;
  tone: "sun" | "leaf" | "primary";
}
