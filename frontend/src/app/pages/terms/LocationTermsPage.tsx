import locationContent from '../../../assets/terms/location-terms.md?raw';
import TermsMarkdownPage from './TermsMarkdownPage';

export default function LocationTermsPage() {
  return <TermsMarkdownPage title="위치정보 이용약관" content={locationContent} />;
}
