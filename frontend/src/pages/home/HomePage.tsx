import { BoothPreviewSection } from "../../components/home/BoothPreviewSection";
import { HeroSection } from "../../components/home/HeroSection";
import { MyActivitySection } from "../../components/home/MyActivitySection";
import { QuickMenuSection } from "../../components/home/QuickMenuSection";
import { UpcomingFairSection } from "../../components/home/UpcomingFairSection";
import { UsageGuideSection } from "../../components/home/UsageGuideSection";
import { PageContainer } from "../../components/common/PageContainer";

export function HomePage() { return <PageContainer className="space-y-14 py-7 sm:py-10"><HeroSection /><QuickMenuSection /><UpcomingFairSection /><MyActivitySection /><BoothPreviewSection /><UsageGuideSection /></PageContainer>; }
