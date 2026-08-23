import { Badge } from "../ui/Badge";
import type { FairStatus } from "../../types/domain";

const labels: Record<FairStatus, string> = { UPCOMING: "예정", RESERVATION_OPEN: "사전예약 중", IN_PROGRESS: "진행 중", ENDED: "종료" };
const tones: Record<FairStatus, "sun" | "primary" | "leaf" | "neutral"> = { UPCOMING: "sun", RESERVATION_OPEN: "primary", IN_PROGRESS: "leaf", ENDED: "neutral" };

export function StatusBadge({ status }: { status: FairStatus }) { return <Badge tone={tones[status]}>{labels[status]}</Badge>; }
