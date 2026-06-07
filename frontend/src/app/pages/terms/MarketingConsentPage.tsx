import marketingContent from '../../../assets/terms/marketing-consent.md?raw';
import TermsMarkdownPage from './TermsMarkdownPage';

export default function MarketingConsentPage() {
  return <TermsMarkdownPage title="마케팅 정보 수신 동의" content={marketingContent} />;
}
