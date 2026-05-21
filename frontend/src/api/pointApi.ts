import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";

export type PointTransactionType = "JOIN_BONUS" | "DEPOSIT" | "EDIT_DEPOSIT" | "REFUND" | "PARTIAL_REFUND" | "PENALTY";

export interface PointTransactionResponse {
    transactionId: number;
    userId: number;
    matchId: number | null;
    amount: number;
    transactionType: PointTransactionType;
    balanceAfter: number;
    description: string;
    createdAt: string;
}

// 포인트 거래 내역 조회
export const getPointTransactions = (type?: PointTransactionType, page: number = 0, size: number = 20) =>
    axiosInstance.get<ApiResponse<PageResponse<PointTransactionResponse>>>("/api/v1/me/points/transactions", {
        params: { status: type, page, size } // spec uses 'status' query param for transaction type
    });
