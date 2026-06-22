package com.example.team3final.domain.chat.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 채팅 메시지 욕설 필터링 서비스
 * - badwords.txt 파일에서 금칙어 목록 로드
 * - 메시지에 금칙어 포함 시 *** 로 마스킹 처리
 * - 향후 AI 마스킹으로 교체 예정 (현재는 단순 키워드 필터링)
 */
@Slf4j
@Service
public class BadWordFilterService {

    // 금칙어 목록
    // Set 사용 이유: List보다 contains() 검색 속도가 빠름 (O(1) vs O(n))
    // 채팅 메시지마다 금칙어 검사하므로 성능 중요
    private final Set<String> badWords = new HashSet<>();

    /**
     * 앱 시작 시 금칙어 파일 로드
     * @PostConstruct: Spring Bean 초기화 완료 후 자동 실행
     * → 매 메시지마다 파일을 읽지 않고 메모리에 한 번만 올려두어 성능 최적화
     * → badwords.txt 수정 시 앱 재시작 필요
     */
    @PostConstruct
    public void loadBadWords() {
        try {
            // ClassPathResource: resources 폴더의 파일을 classpath에서 읽어옴
            ClassPathResource resource = new ClassPathResource("yok/badwords.txt");

            // UTF-8로 읽기 (한글 깨짐 방지)
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );

            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim(); // 앞뒤 공백 제거
                if (!word.isEmpty()) {    // 빈 줄 스킵
                    badWords.add(word);
                }
            }
            reader.close();
            log.info("[BadWordFilter] 금칙어 {}개 로드 완료", badWords.size());

        } catch (Exception e) {
            log.error("[BadWordFilter] 금칙어 파일 로드 실패", e);
        }
    }

    /**
     * 메시지에서 금칙어를 ***로 마스킹
     * - 금칙어가 여러 개 포함된 경우 모두 마스킹
     * - 대소문자 구분 (현재는 단순 contains() 검사)
     * - 향후 정규식 또는 AI로 교체 시 이 메서드만 수정하면 됨
     *
     * @param message 원본 메시지
     * @return 마스킹된 메시지 (금칙어 없으면 원본 그대로 반환)
     */
    public String filter(String message) {

        // null 또는 빈 메시지는 그대로 반환
        if (message == null || message.isEmpty()) {
            return message;
        }

        String filtered = message;
        for (String badWord : badWords) {
            // 메시지에 금칙어가 포함된 경우 *** 로 교체
            // replace(): 해당 금칙어를 모두 교체 (replaceAll과 달리 정규식 아님)
            if (filtered.contains(badWord)) {
                filtered = filtered.replace(badWord, "***");
            }
        }
        return filtered;
    }
}