import privacyContent from '../../../assets/terms/privacy-policy.md?raw';
import TermsMarkdownPage from './TermsMarkdownPage';

export default function PrivacyPolicyPage() {
  return <TermsMarkdownPage title="개인정보처리방침" content={privacyContent} />;
}
