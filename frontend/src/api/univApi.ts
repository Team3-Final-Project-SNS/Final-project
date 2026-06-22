import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export interface UniversityResponse {
    universityId: number;
    universityName: string;
    eDomain: string;
}

export const getUniversities = () =>
    axiosInstance.get<ApiResponse<UniversityResponse[]>>("/api/v1/universities");
