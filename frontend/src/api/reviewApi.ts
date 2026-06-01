import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export type ReviewGoodTag =
  | "ON_TIME"
  | "KIND"
  | "GOOD_COMMUNICATION"
  | "CLEAN_MANNER"
  | "WANT_MEET_AGAIN";

export type ReviewBadTag =
  | "LATE"
  | "NO_REPLY"
  | "UNCOMFORTABLE"
  | "BAD_MANNER"
  | "REPORT_NEEDED";

export interface CreateReviewRequest {
  goodTags: ReviewGoodTag[];
  badTags: ReviewBadTag[];
}

export interface CreateReviewResponse {
  reviewId: number;
  matchId: number;
  targetId: number;
  targetNickname: string;
  goodTags: ReviewGoodTag[];
  badTags: ReviewBadTag[];
  tagScoreDelta: number;
  reportNeeded: boolean;
  rewardPoint: number;
  createdAt: string;
}

export interface ReviewItem {
  reviewId: number;
  matchId: number;
  writerId: number;
  writerNickname: string;
  goodTags: ReviewGoodTag[];
  badTags: ReviewBadTag[];
  tagScoreDelta: number;
  reportNeeded: boolean;
  createdAt: string;
}

export interface GetReceivedReviewsResponse {
  userId: number;
  nickname: string;
  mannerTemperature: number;
  content: ReviewItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export const createReview = (matchId: number, data: CreateReviewRequest) =>
  axiosInstance.post<ApiResponse<CreateReviewResponse>>(`/api/v1/matches/${matchId}/reviews`, data);

export const getReceivedReviews = (userId: number, page: number = 0, size: number = 10) =>
  axiosInstance.get<ApiResponse<GetReceivedReviewsResponse>>(`/api/v1/users/${userId}/reviews`, {
    params: { page, size },
  });
