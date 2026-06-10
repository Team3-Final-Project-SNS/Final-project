import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export type PostStatus = "OPEN" | "MATCHED" | "COMPLETED" | "CANCELLED" | "EXPIRED";

export interface PostItemResponse {
    postId: number;
    authorId: number;
    authorNickname: string;
    authorMajor: string;
    authorStudentNumber: string;
    authorMannerTemperature: number | null;
    meetAt: string;
    placeName: string;
    authorDeposit: number;
    currentApplicants: number;
    maxApplicants: number;
    status: PostStatus;
    createAt: string; // backend says createAt
    createdAt?: string;
}

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
}

export interface GetPostResponse {
    postId: number;
    authorId: number;
    authorNickname: string;
    authorMajor: string;
    authorStudentNumber: string;
    meetAt: string;
    placeName: string;
    placeLat: number;
    placeLng: number;
    content: string;
    authorDeposit: number;
    currentApplicants: number;
    maxApplicants: number;
    status: PostStatus;
    isMine: boolean;
    createAt: string; // backend says createAt
    createdAt?: string;
    updateAt: string; // backend says updateAt
}

export interface CreatePostRequest {
    meetAt: string;
    placeName: string;
    placeLat: number;
    placeLng: number;
    content?: string;
    authorDeposit: number;
    maxApplicants: number;
}

export interface UpdatePostRequest {
    meetAt?: string;
    placeName?: string;
    placeLat?: number;
    placeLng?: number;
    content?: string;
    authorDeposit?: number;
}

export interface DeletedPostReasonResponse {
    postId: number;
    placeName: string;
    deleteReason: string;
    deletedAt: string;
}

// 게시글 작성
export const createPost = (data: CreatePostRequest) =>
    axiosInstance.post<ApiResponse<{ postId: number }>>("/api/v1/posts", data);

// 게시글 목록 조회
export const getPosts = (status: PostStatus = "OPEN", page: number = 0, size: number = 20) =>
    axiosInstance.get<ApiResponse<PageResponse<PostItemResponse>>>(`/api/v1/posts`, {
        params: { status, page, size }
    });

// 게시글 상세 조회
export const getPost = (postId: number) =>
    axiosInstance.get<ApiResponse<GetPostResponse>>(`/api/v1/posts/${postId}`);

// 게시글 수정
export const updatePost = (postId: number, data: UpdatePostRequest) =>
    axiosInstance.patch<ApiResponse<{ postId: number }>>(`/api/v1/posts/${postId}`, data);

// 게시글 삭제
export const deletePost = (postId: number) =>
    axiosInstance.delete<ApiResponse<{ postId: number, refundedPoint: number }>>(`/api/v1/posts/${postId}`);

export const getDeletedPostReason = (postId: number) =>
    axiosInstance.get<ApiResponse<DeletedPostReasonResponse>>(`/api/v1/posts/${postId}/delete-reason`);
