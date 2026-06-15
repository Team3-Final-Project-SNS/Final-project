import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export type PostStatus = "OPEN" | "MATCHED" | "COMPLETED" | "CANCELLED" | "EXPIRED" | "DELETED";

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

export interface CreatePostResponse {
    postId: number;
    authorId: number;
    authorNickname: string;
    meetAt: string;
    placeName: string;
    placeLat: number;
    placeLng: number;
    content: string | null;
    authorDeposit: number;
    status: PostStatus;
    createdAt: string;
}

export interface UpdatePostRequest {
    meetAt?: string;
    placeName?: string;
    placeLat?: number;
    placeLng?: number;
    content?: string;
    authorDeposit?: number;
}

export interface UpdatePostResponse {
    postId: number;
    meetAt: string;
    placeName: string;
    authorDeposit: number;
    status: PostStatus;
    updatedAt: string;
}

export interface DeletedPostReasonResponse {
    postId: number;
    placeName: string;
    deleteReason: string;
    deletedAt: string;
}

export interface DeletePostResponse {
    postId: number;
    refundedPoint: number;
}

// 寃뚯떆湲 ?묒꽦
export const createPost = (data: CreatePostRequest) =>
    axiosInstance.post<ApiResponse<CreatePostResponse>>("/api/v1/posts", data);

// 寃뚯떆湲 紐⑸줉 議고쉶
export const getPosts = (status: PostStatus = "OPEN", page: number = 0, size: number = 20) =>
    axiosInstance.get<ApiResponse<PageResponse<PostItemResponse>>>(`/api/v1/posts`, {
        params: { status, page, size }
    });

// 寃뚯떆湲 ?곸꽭 議고쉶
export const getPost = (postId: number) =>
    axiosInstance.get<ApiResponse<GetPostResponse>>(`/api/v1/posts/${postId}`);

// 寃뚯떆湲 ?섏젙
export const updatePost = (postId: number, data: UpdatePostRequest) =>
    axiosInstance.patch<ApiResponse<UpdatePostResponse>>(`/api/v1/posts/${postId}`, data);

// 寃뚯떆湲 ??젣
export const deletePost = (postId: number) =>
    axiosInstance.delete<ApiResponse<DeletePostResponse>>(`/api/v1/posts/${postId}`);

export const getDeletedPostReason = (postId: number) =>
    axiosInstance.get<ApiResponse<DeletedPostReasonResponse>>(`/api/v1/posts/${postId}/delete-reason`);
