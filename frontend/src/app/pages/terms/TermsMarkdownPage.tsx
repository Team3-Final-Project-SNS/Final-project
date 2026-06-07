import ReactMarkdown from 'react-markdown';
import { Link } from 'react-router';

type TermsMarkdownPageProps = {
  title: string;
  content: string;
};

export default function TermsMarkdownPage({ title, content }: TermsMarkdownPageProps) {
  return (
    <main className="min-h-screen bg-[#fafafa] px-5 py-10">
      <section className="mx-auto max-w-3xl rounded-2xl bg-white px-6 py-8 shadow-sm md:px-10 md:py-12">
        <div className="mb-8 flex flex-col gap-4 border-b border-[#eeeeee] pb-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm font-semibold text-[#d84315]">한끼팟 약관</p>
            <h1 className="mt-2 text-3xl font-bold text-[#212121]">{title}</h1>
          </div>
          <Link
            to="/signup"
            className="inline-flex w-fit items-center justify-center rounded-lg border border-[#d84315] px-4 py-2 text-sm font-semibold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
          >
            회원가입으로 돌아가기
          </Link>
        </div>

        <ReactMarkdown
          className="space-y-4 text-[15px] leading-8 text-[#424242] [&_h1]:mb-6 [&_h1]:text-3xl [&_h1]:font-extrabold [&_h1]:text-[#212121] [&_h2]:mb-3 [&_h2]:mt-8 [&_h2]:text-xl [&_h2]:font-bold [&_h2]:text-[#212121] [&_h3]:mb-2 [&_h3]:mt-6 [&_h3]:text-lg [&_h3]:font-bold [&_h3]:text-[#212121] [&_li]:ml-5 [&_li]:list-disc [&_ol>li]:list-decimal [&_p]:my-3 [&_strong]:font-bold [&_strong]:text-[#212121] [&_ul]:space-y-2"
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
      </section>
    </main>
  );
}
