import React from "react";
import { createBrowserRouter } from "react-router";
import Layout from "./components/Layout";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import AdminLoginPage from "./pages/AdminLoginPage";
import AdminHomePage from "./pages/AdminHomePage";
import AdminReportsPage from "./pages/AdminReportsPage";
import AdminInquiriesPage from "./pages/AdminInquiriesPage";
import AdminDisputesPage from "./pages/AdminDisputesPage";
import AdminUsersPage from "./pages/AdminUsersPage";
import AdminPostsPage from "./pages/AdminPostsPage";
import AdminPaymentsPage from "./pages/AdminPaymentsPage";
import AdminFaqPage from "./pages/AdminFaqPage";
import AdminLayout from "./components/AdminLayout";
import SignupPage from "./pages/SignupPage";
import PostListPage from "./pages/PostListPage";
import PostDetailPage from "./pages/PostDetailPage";
import DeletedPostReasonPage from "./pages/DeletedPostReasonPage";
import PostCreatePage from "./pages/PostCreatePage";
import MatchesPage from "./pages/MatchesPage";
import MatchDetailPage from "./pages/MatchDetailPage";
import ChatPage from "./pages/ChatPage";
import MyInfoPage from "./pages/MyInfoPage";
import MyInfoEditPage from "./pages/MyInfoEditPage";
import PointTransactionsPage from "./pages/PointTransactionsPage";
import QRVerificationPage from "./pages/QRVerificationPage";
import PlaceVerificationPage from "./pages/PlaceVerificationPage";
import MatchingAiChatPage from "./pages/MatchingAiChatPage";
import InquiryCenterPage from "./pages/InquiryCenterPage";
import ReportCenterPage from "./pages/ReportCenterPage";
import PaymentPage from "./pages/PaymentPage";
import NotFoundPage from "./pages/NotFoundPage";
import TermsOfServicePage from "./pages/terms/TermsOfServicePage";
import PrivacyPolicyPage from "./pages/terms/PrivacyPolicyPage";
import LocationTermsPage from "./pages/terms/LocationTermsPage";
import MarketingConsentPage from "./pages/terms/MarketingConsentPage";
import TermsMarkdownPage from "./pages/terms/TermsMarkdownPage";
import faqContent from "../assets/terms/FAQ.md?raw";
import RequireAuth from "./components/RequireAuth";

export const router = createBrowserRouter([
  {
    // 메인 페이지: 로그인 여부와 무관하게 누구나 접근 가능
    // (RequireAuth 밖에 있는 유일한 일반 페이지)
    path: "/",
    Component: HomePage,
  },
  {
    // [핵심] 이 컴포넌트 하위의 모든 children 라우트는 인증 필요
    // RequireAuth: 토큰 없으면 <Navigate to="/login" /> 으로 자동 전환
    // path를 지정하지 않은 "레이아웃 라우트"이므로 URL에는 영향 없음
    Component: RequireAuth,
    children: [
      {
        // [이동+병합] 기존 최상위 "/posts" 블록(보호 안 됨)을 이 위치로 이동
        // path는 "/posts" 그대로 유지 → URL 변경 없이 보호만 추가됨
        // 기존 "/app/posts" 블록은 어디서도 참조되지 않는 죽은 코드라 제거함
        path: "/posts",
        Component: Layout,
        children: [
          { index: true, Component: PostListPage },
          { path: "new", Component: PostCreatePage },
          { path: ":id/edit", Component: PostCreatePage },
          { path: ":id", Component: PostDetailPage },
          { path: ":id/delete-reason", Component: DeletedPostReasonPage },
        ],
      },
      {
        // 매칭 관련: 기존과 동일한 위치 유지 (이미 RequireAuth 안에 있었음)
        path: "/matches",
        Component: Layout,
        children: [
          { index: true, Component: MatchesPage },
          { path: ":id", Component: MatchDetailPage },
          { path: ":id/place-verification", Component: PlaceVerificationPage },
          { path: ":id/qr", Component: QRVerificationPage },
        ],
      },
      {
        // 결제: 기존과 동일
        path: "/payments",
        Component: Layout,
        children: [
          { index: true, Component: PaymentPage },
        ],
      },
      {
        // AI 매칭: 기존과 동일
        path: "/ai",
        Component: Layout,
        children: [
          { path: "matching", Component: MatchingAiChatPage },
        ],
      },
      {
        // 채팅: 기존과 동일
        path: "/chat/:roomId",
        Component: Layout,
        children: [
          { index: true, Component: ChatPage },
        ],
      },
      {
        // 내 정보: 기존과 동일
        path: "/me",
        Component: Layout,
        children: [
          { index: true, Component: MyInfoPage },
          { path: "edit", Component: MyInfoEditPage },
          { path: "points", Component: PointTransactionsPage },
          { path: "matches", Component: MatchesPage },
          { path: "inquiries", Component: InquiryCenterPage },
          { path: "support", Component: InquiryCenterPage },
          { path: "support/inquiries", Component: InquiryCenterPage },
          { path: "support/disputes/no-show", Component: InquiryCenterPage },
          {
            path: "support/faq",
            element: (
              <TermsMarkdownPage
                title="자주 묻는 질문"
                content={faqContent}
                eyebrow="FAQ"
                backTo="/me/support"
                backLabel="고객센터"
                variant="support"
              />
            ),
          },
          { path: "reports", Component: ReportCenterPage },
        ],
      },
      {
        path: "/faq",
        Component: Layout,
        children: [
          {
            index: true,
            element: (
              <TermsMarkdownPage
                title="자주 묻는 질문"
                content={faqContent}
                eyebrow="FAQ"
                backTo="/me/support"
                backLabel="고객센터"
                variant="support"
              />
            ),
          },
        ],
      },
    ],
  },
  {
    path: "/login",
    Component: LoginPage,
  },
  {
    path: "/admin/login",
    Component: AdminLoginPage,
  },
  {
    path: "/admin",
    Component: AdminLayout,
    children: [
      { index: true, Component: AdminHomePage },
      { path: "posts", Component: AdminPostsPage },
      { path: "users", Component: AdminUsersPage },
      { path: "reports", Component: AdminReportsPage },
      { path: "disputes", Component: AdminDisputesPage },
      { path: "inquiries", Component: AdminInquiriesPage },
      { path: "payments", Component: AdminPaymentsPage },
      { path: "faq", Component: AdminFaqPage },
    ],
  },
  {
    path: "/signup",
    Component: SignupPage,
  },
  {
    path: "/terms/service",
    Component: TermsOfServicePage,
  },
  {
    path: "/terms/privacy",
    Component: PrivacyPolicyPage,
  },
  {
    path: "/terms/location",
    Component: LocationTermsPage,
  },
  {
    path: "/terms/marketing",
    Component: MarketingConsentPage,
  },
  {
    path: "*",
    Component: NotFoundPage,
  },
]);
