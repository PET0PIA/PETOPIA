import { BoothPreviewSection } from "../../components/home/BoothPreviewSection";
import { HeroSection } from "../../components/home/HeroSection";
import { HomeNoticeMarquee } from "../../components/home/HomeNoticeMarquee";
import { PopupModal } from "../../components/home/PopupModal";
import { QuickMenuSection } from "../../components/home/QuickMenuSection";
import { UpcomingFairSection } from "../../components/home/UpcomingFairSection";
import { FairReviewSection } from "../../components/home/FairReviewSection";
import { PetopiaNewsSection } from "../../components/home/PetopiaNewsSection";
import { PageContainer } from "../../components/common/PageContainer";

export function HomePage() { return <><PopupModal /><HeroSection /><HomeNoticeMarquee /><PageContainer className="space-y-14 py-7 sm:py-10"><QuickMenuSection /><UpcomingFairSection /><BoothPreviewSection /><FairReviewSection /><PetopiaNewsSection /></PageContainer></>; }
