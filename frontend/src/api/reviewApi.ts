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
  | "DO_NOT_WANT_TO_MEET_AGAIN";

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
  doNotWantToMeetAgainSelected: boolean;
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
  doNotWantToMeetAgainSelected: boolean;
  createdAt: string;
}

export interface GetWrittenReviewsResponse {
  userId: number;
  nickname: string;
  content: ReviewItem[];
}

export const createReview = (matchId: number, data: CreateReviewRequest) =>
  axiosInstance.post<ApiResponse<CreateReviewResponse>>(`/api/v1/matches/${matchId}/reviews`, data);

export const getMyWrittenReviews = () =>
  axiosInstance.get<ApiResponse<GetWrittenReviewsResponse>>(`/api/v1/me/reviews`);
