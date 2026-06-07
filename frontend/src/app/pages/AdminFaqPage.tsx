import { useMemo, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Link } from 'react-router';
import { ArrowLeft, FileQuestion } from 'lucide-react';
import AdminFloatingChatbot from '../components/AdminFloatingChatbot';
import faqContent from '../../assets/terms/FAQ.md?raw';

type FaqSection = {
  number: string;
  title: string;
  content: string;
};

export default function AdminFaqPage() {
  const sections = useMemo(() => parseFaqSections(faqContent), []);
  const lastUpdated = useMemo(() => parseLastUpdated(faqContent), []);
  const [selectedSectionNumber, setSelectedSectionNumber] = useState(sections[0]?.number ?? '1');
  const contentRef = useRef<HTMLDivElement>(null);
  const selectedSection = sections.find((section) => section.number === selectedSectionNumber) ?? sections[0];

  const handleSelectSection = (sectionNumber: string) => {
    setSelectedSectionNumber(sectionNumber);
    window.setTimeout(() => {
      contentRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 0);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
      <main className="mx-auto max-w-screen-lg px-4 py-10">
        <Link to="/admin" className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] hover:text-[#d84315]">
          <ArrowLeft size={16} />
          관리자 콘솔
        </Link>

        <section className="overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
          <div className="border-b border-[#eeeeee] px-7 py-6">
            <div className="flex items-center gap-4">
              <div className="flex h-14 w-14 items-center justify-center rounded-full bg-[#fff3e0]">
                <FileQuestion className="text-[#d84315]" size={30} />
              </div>
              <div>
                <p className="text-sm font-semibold text-[#d84315]">관리자 콘솔</p>
                <h1 className="mt-1 text-3xl font-bold text-[#212121]">FAQ</h1>
                <p className="mt-2 text-sm leading-6 text-[#757575]">
                  사용자 응대와 운영 안내에 활용할 자주 묻는 질문입니다.
                </p>
              </div>
            </div>
            {lastUpdated && (
              <div className="mt-5 rounded-lg border-l-4 border-[#d84315] bg-[#fff7ed] px-4 py-3 text-sm font-semibold text-[#424242]">
                마지막 업데이트: {lastUpdated}
              </div>
            )}
          </div>

          <div className="px-7 py-8">
            <div className="mb-8">
              <div className="mb-4 flex items-end justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-bold text-[#212121]">목차</h2>
                  <p className="mt-2 text-sm text-[#757575]">번호를 선택하면 해당 FAQ만 아래에 표시됩니다.</p>
                </div>
                <span className="text-sm font-semibold text-[#d84315]">
                  {selectedSection?.number}. {selectedSection?.title}
                </span>
              </div>

              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {sections.map((section) => {
                  const isSelected = section.number === selectedSection?.number;

                  return (
                    <button
                      key={section.number}
                      type="button"
                      onClick={() => handleSelectSection(section.number)}
                      className={`flex min-h-16 items-center gap-3 rounded-xl border px-4 py-3 text-left transition-all ${
                        isSelected
                          ? 'border-[#d84315] bg-[#fff3e0] text-[#d84315] shadow-sm'
                          : 'border-[#eeeeee] bg-[#fafafa] text-[#424242] hover:border-[#d84315] hover:bg-[#fff7ed]'
                      }`}
                    >
                      <span
                        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-extrabold ${
                          isSelected ? 'bg-[#d84315] text-white' : 'bg-white text-[#d84315]'
                        }`}
                      >
                        {section.number}
                      </span>
                      <span className="text-sm font-bold leading-5">{section.title}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            <div ref={contentRef} className="rounded-2xl border border-[#eeeeee] bg-[#fffdf9] p-6">
              <div className="mb-6 border-b border-[#eeeeee] pb-4">
                <p className="text-sm font-semibold text-[#d84315]">FAQ {selectedSection?.number}</p>
                <h2 className="mt-2 text-2xl font-extrabold text-[#212121]">{selectedSection?.title}</h2>
              </div>

              <div className="space-y-4 text-[15px] leading-8 text-[#424242] [&_a]:font-semibold [&_a]:text-[#d84315] [&_a]:underline [&_h3]:mb-2 [&_h3]:mt-7 [&_h3]:text-lg [&_h3]:font-bold [&_h3]:text-[#212121] [&_hr]:my-8 [&_li]:ml-5 [&_li]:list-disc [&_ol>li]:list-decimal [&_p]:my-3 [&_strong]:font-bold [&_strong]:text-[#212121] [&_table]:w-full [&_table]:min-w-[520px] [&_table]:border-collapse [&_tbody_tr:nth-child(even)]:bg-white [&_td]:border [&_td]:border-[#eeeeee] [&_td]:px-4 [&_td]:py-3 [&_td]:align-top [&_th]:border [&_th]:border-[#eeeeee] [&_th]:bg-[#fff3e0] [&_th]:px-4 [&_th]:py-3 [&_th]:text-left [&_th]:font-extrabold [&_th]:text-[#212121] [&_ul]:space-y-2">
                <ReactMarkdown
                  remarkPlugins={[remarkGfm]}
                  components={{
                    table: ({ children }: { children?: React.ReactNode }) => (
                        <div className="my-5 overflow-x-auto rounded-xl border border-[#eeeeee] bg-white">
                          <table>{children}</table>
                        </div>
                    ),
                  }}
                >
                  {selectedSection?.content ?? ''}
                </ReactMarkdown>
              </div>
            </div>
          </div>
        </section>
      </main>
      <AdminFloatingChatbot />
    </div>
  );
}

function parseLastUpdated(markdown: string) {
  const match = markdown.match(/마지막 업데이트:\s*([0-9.]+)/);

  return match?.[1] ?? '';
}

function parseFaqSections(markdown: string): FaqSection[] {
  const sectionHeadingRegex = /^##\s+(\d+)\.\s+(.+)$/gm;
  const headings = [...markdown.matchAll(sectionHeadingRegex)];

  return headings.map((heading, index) => {
    const start = heading.index ?? 0;
    const nextStart = headings[index + 1]?.index ?? markdown.length;
    const block = markdown.slice(start, nextStart).trim();
    const content = block.replace(/^##\s+\d+\.\s+.+\n?/, '').trim();

    return {
      number: heading[1],
      title: heading[2].trim(),
      content,
    };
  });
}
