import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";

export type PointTransactionType =
    | "JOIN_BONUS"
    | "CHARGE"
    | "CHARGE_CANCELLED"
    | "DEPOSIT"
    | "EDIT_DEPOSIT"
    | "REFUND"
    | "PARTIAL_REFUND"
    | "PENALTY"
    | "REPORT_REWARD"
    | "REVIEW_REWARD";

export type PointReferenceType = "MATCH" | "POST" | "PAYMENT";
export type PointSettlementReason = "APPLICANT_DEPOSIT" | "AUTHOR_DEPOSIT";

export interface PointTransactionResponse {
    transactionId: number;
    userId: number;
    matchId: number | null;
    referenceType: PointReferenceType | null;
    referenceId: number | null;
    settlementReason: PointSettlementReason | null;
    amount: number;
    transactionType: PointTransactionType;
    balanceAfter: number;
    description: string | null;
    createdAt: string;
}

// 포인트 거래 내역 조회
export const getPointTransactions = (type?: PointTransactionType, page: number = 0, size: number = 20) =>
    axiosInstance.get<ApiResponse<PageResponse<PointTransactionResponse>>>("/api/v1/me/points/transactions", {
        params: { type, page, size }
    });
