import { useMemo, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Link } from 'react-router';
import { ArrowLeft } from 'lucide-react';

type TermsMarkdownPageProps = {
  title: string;
  content: string;
  eyebrow?: string;
  backTo?: string;
  backLabel?: string;
  variant?: 'terms' | 'support';
};

export default function TermsMarkdownPage({
  title,
  content,
  eyebrow = '한끼팟 약관',
  backTo = '/signup',
  backLabel = '회원가입으로 돌아가기',
  variant = 'terms',
}: TermsMarkdownPageProps) {
  const isSupport = variant === 'support';
  const containerClass = isSupport
    ? 'mx-auto max-w-5xl'
    : 'min-h-screen bg-[#fafafa] px-5 py-10';
  const sectionClass = isSupport
    ? 'rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm'
    : 'mx-auto max-w-3xl rounded-2xl bg-white px-6 py-8 shadow-sm md:px-10 md:py-12';
  const faqSections = useMemo(() => parseFaqSections(content), [content]);
  const [selectedFaqIndex, setSelectedFaqIndex] = useState(0);
  const selectedFaqSection = faqSections[selectedFaqIndex] ?? faqSections[0];

  return (
    <main className={containerClass}>
      {isSupport && (
        <>
          <div className="mb-6">
            <h1 className="text-3xl font-bold text-[#212121]">고객센터</h1>
            <p className="mt-2 text-sm text-[#757575]">필요한 고객센터 업무를 선택해 진행할 수 있습니다.</p>
          </div>

          <Link
            to={backTo}
            className="mb-5 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] transition-colors hover:text-[#d84315]"
          >
            <ArrowLeft size={16} />
            {backLabel}
          </Link>
        </>
      )}

      {isSupport && selectedFaqSection ? (
        <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
          <section className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-5 text-lg font-bold text-[#212121]">목차</h2>
            <div className="max-h-[520px] space-y-2 overflow-y-auto pr-1">
              {faqSections.map((section, index) => (
                <button
                  key={section.title}
                  type="button"
                  onClick={() => setSelectedFaqIndex(index)}
                  className={`flex w-full items-center justify-between gap-3 rounded-xl border px-4 py-3 text-left text-sm font-bold transition-colors ${
                    selectedFaqIndex === index
                      ? 'border-[#d84315] bg-[#fff3e0] text-[#d84315]'
                      : 'border-[#eeeeee] bg-white text-[#424242] hover:border-[#d84315] hover:bg-[#fffaf7] hover:text-[#d84315]'
                  }`}
                >
                  <span>{section.title}</span>
                </button>
              ))}
            </div>
          </section>

          <section className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-5 text-lg font-bold text-[#212121]">{selectedFaqSection.title}</h2>
            <div className="max-h-[520px] overflow-x-auto overflow-y-auto rounded-xl border border-dashed border-[#e0e0e0] px-5 py-4">
              <div className="space-y-4 text-[15px] leading-8 text-[#424242] [&_h3]:mb-2 [&_h3]:mt-6 [&_h3]:text-lg [&_h3]:font-bold [&_h3]:text-[#212121] [&_li]:ml-5 [&_li]:list-disc [&_ol>li]:list-decimal [&_p]:my-3 [&_strong]:font-bold [&_strong]:text-[#212121] [&_table]:w-full [&_table]:border-collapse [&_td]:border [&_td]:border-[#eeeeee] [&_td]:px-3 [&_td]:py-2 [&_th]:border [&_th]:border-[#eeeeee] [&_th]:bg-[#fafafa] [&_th]:px-3 [&_th]:py-2 [&_ul]:space-y-2">
                <ReactMarkdown
                  remarkPlugins={[remarkGfm]}
                  components={{
                    a: ({ children, href }) => (
                      <a className="font-semibold text-[#d84315] underline" href={href}>
                        {children}
                      </a>
                    ),
                  }}
                >
                  {selectedFaqSection.body}
                </ReactMarkdown>
              </div>
            </div>
          </section>
        </div>
      ) : (
      <section className={sectionClass}>
        <div className={`mb-8 border-b border-[#eeeeee] pb-6 ${
          isSupport ? '' : 'flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between'
        }`}>
          <div>
            <p className="text-sm font-semibold text-[#d84315]">{eyebrow}</p>
            <h1 className="mt-2 text-3xl font-bold text-[#212121]">{title}</h1>
          </div>
          {!isSupport && (
            <Link
              to={backTo}
              className="inline-flex w-fit items-center justify-center rounded-lg border border-[#d84315] px-4 py-2 text-sm font-semibold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
            >
              {backLabel}
            </Link>
          )}
        </div>

        <div className="overflow-x-auto space-y-4 text-[15px] leading-8 text-[#424242] [&_h1]:mb-6 [&_h1]:text-3xl [&_h1]:font-extrabold [&_h1]:text-[#212121] [&_h2]:mb-3 [&_h2]:mt-8 [&_h2]:text-xl [&_h2]:font-bold [&_h2]:text-[#212121] [&_h3]:mb-2 [&_h3]:mt-6 [&_h3]:text-lg [&_h3]:font-bold [&_h3]:text-[#212121] [&_li]:ml-5 [&_li]:list-disc [&_ol>li]:list-decimal [&_p]:my-3 [&_strong]:font-bold [&_strong]:text-[#212121] [&_ul]:space-y-2">
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              a: ({ children, href }) => (
                <a className="font-semibold text-[#d84315] underline" href={href}>
                  {children}
                </a>
              ),
            }}
          >
            {content}
          </ReactMarkdown>
        </div>
      </section>
      )}
    </main>
  );
}

function parseFaqSections(content: string) {
  const sectionMatches = [...content.matchAll(/^##\s+(\d+\.\s+.+)$/gm)];

  if (sectionMatches.length === 0) {
    return [{ title: 'FAQ', body: content }];
  }

  return sectionMatches.map((match, index) => {
    const start = match.index ?? 0;
    const nextStart = sectionMatches[index + 1]?.index ?? content.length;
    const sectionText = content.slice(start, nextStart).trim();
    const body = sectionText.replace(/^##\s+.+\n?/, '').trim();

    return {
      title: match[1].trim(),
      body,
    };
  });
}
