import termsContent from '../../../assets/terms/terms-of-service.md?raw';
import TermsMarkdownPage from './TermsMarkdownPage';

export default function TermsOfServicePage() {
  return <TermsMarkdownPage title="서비스 이용약관" content={termsContent} />;
}
