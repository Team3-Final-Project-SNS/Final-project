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
import AdminComingSoonPage from "./pages/AdminComingSoonPage";
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
import MyMatchResultsPage from "./pages/MyMatchResultsPage";
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
import RequireAuth from "./components/RequireAuth";

export const router = createBrowserRouter([
  {
    path: "/",
    Component: HomePage,
  },
  {
    Component: RequireAuth,
    children: [
      {
        path: "/app",
        Component: Layout,
        children: [
          { path: "posts", Component: PostListPage },
          { path: "posts/new", Component: PostCreatePage },
          { path: "posts/:id/edit", Component: PostCreatePage },
          { path: "posts/:id", Component: PostDetailPage },
          { path: "posts/:id/delete-reason", Component: DeletedPostReasonPage },
          { path: "matches", Component: MatchesPage },
          { path: "matches/:id", Component: MatchDetailPage },
          { path: "ai/matching", Component: MatchingAiChatPage },
          { path: "chat/:roomId", Component: ChatPage },
          { path: "me", Component: MyInfoPage },
          { path: "me/edit", Component: MyInfoEditPage },
          { path: "me/points", Component: PointTransactionsPage },
          { path: "me/matches", Component: MyMatchResultsPage },
          { path: "me/inquiries", Component: InquiryCenterPage },
          { path: "me/reports", Component: ReportCenterPage },
          { path: "matches/:id/qr", Component: QRVerificationPage },
        ],
      },
      {
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
        path: "/payments",
        Component: Layout,
        children: [
          { index: true, Component: PaymentPage },
        ],
      },
      {
        path: "/ai",
        Component: Layout,
        children: [
          { path: "matching", Component: MatchingAiChatPage },
        ],
      },
      {
        path: "/chat/:roomId",
        Component: Layout,
        children: [
          { index: true, Component: ChatPage },
        ],
      },
      {
        path: "/me",
        Component: Layout,
        children: [
          { index: true, Component: MyInfoPage },
          { path: "edit", Component: MyInfoEditPage },
          { path: "points", Component: PointTransactionsPage },
          { path: "matches", Component: MyMatchResultsPage },
          { path: "inquiries", Component: InquiryCenterPage },
          { path: "reports", Component: ReportCenterPage },
        ],
      },
    ],
  },
  {
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
