import { createBrowserRouter } from "react-router";
import Layout from "./components/Layout";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import AdminLoginPage from "./pages/AdminLoginPage";
import AdminHomePage from "./pages/AdminHomePage";
import AdminReportsPage from "./pages/AdminReportsPage";
import AdminInquiriesPage from "./pages/AdminInquiriesPage";
import SignupPage from "./pages/SignupPage";
import PostListPage from "./pages/PostListPage";
import PostDetailPage from "./pages/PostDetailPage";
import PostCreatePage from "./pages/PostCreatePage";
import MatchesPage from "./pages/MatchesPage";
import ChatPage from "./pages/ChatPage";
import MyInfoPage from "./pages/MyInfoPage";
import MyMatchResultsPage from "./pages/MyMatchResultsPage";
import PointTransactionsPage from "./pages/PointTransactionsPage";
import QRVerificationPage from "./pages/QRVerificationPage";
import PlaceVerificationPage from "./pages/PlaceVerificationPage";
import MatchingAiChatPage from "./pages/MatchingAiChatPage";
import InquiryCenterPage from "./pages/InquiryCenterPage";
import ReportCenterPage from "./pages/ReportCenterPage";
import NotFoundPage from "./pages/NotFoundPage";

export const router = createBrowserRouter([
  {
    path: "/",
    Component: HomePage,
  },
  {
    path: "/app",
    Component: Layout,
    children: [
      { path: "posts", Component: PostListPage },
      { path: "posts/new", Component: PostCreatePage },
      { path: "posts/:id", Component: PostDetailPage },
      { path: "matches", Component: MatchesPage },
      { path: "ai/matching", Component: MatchingAiChatPage },
      { path: "chat/:roomId", Component: ChatPage },
      { path: "me", Component: MyInfoPage },
      { path: "me/points", Component: PointTransactionsPage },
      { path: "me/matches", Component: MyMatchResultsPage },
      { path: "me/inquiries", Component: InquiryCenterPage },
      { path: "me/reports", Component: ReportCenterPage },
      { path: "matches/:id/qr", Component: QRVerificationPage },
    ],
  },
  {
    path: "/posts",
    Component: Layout,
    children: [
      { index: true, Component: PostListPage },
      { path: "new", Component: PostCreatePage },
      { path: ":id", Component: PostDetailPage },
    ],
  },
  {
    path: "/matches",
    Component: Layout,
    children: [
      { index: true, Component: MatchesPage },
      { path: ":id", Component: ChatPage },
      { path: ":id/place-verification", Component: PlaceVerificationPage },
      { path: ":id/qr", Component: QRVerificationPage },
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
      { path: "points", Component: PointTransactionsPage },
      { path: "matches", Component: MyMatchResultsPage },
      { path: "inquiries", Component: InquiryCenterPage },
      { path: "reports", Component: ReportCenterPage },
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
    Component: AdminHomePage,
  },
  {
    path: "/admin/reports",
    Component: AdminReportsPage,
  },
  {
    path: "/admin/inquiries",
    Component: AdminInquiriesPage,
  },
  {
    path: "/signup",
    Component: SignupPage,
  },
  {
    path: "*",
    Component: NotFoundPage,
  },
]);
