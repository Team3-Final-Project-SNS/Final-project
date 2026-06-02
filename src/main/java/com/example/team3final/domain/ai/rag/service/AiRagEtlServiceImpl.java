package com.example.team3final.domain.ai.rag.service;

import com.example.team3final.domain.ai.rag.dto.AiRagIndexResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * classpath 문서를 대상으로 하는 RAG ETL 파이프라인 구현체입니다.
 *
 * 현재 단계에서는 src/main/resources/rag-docs 아래의 .md/.txt 문서를 읽어
 * Spring AI Document로 변환하고, TokenTextSplitter로 chunk를 나눈 뒤
 * VectorStore에 저장합니다. pgvector를 사용하면 Spring AI가 embedding 생성과
 * vector_store 저장을 처리합니다.
 *
 * VectorStore Bean이 있을 때만 생성되므로, pgvector 설정이 아직 없는 환경에서도
 * 애플리케이션 전체가 깨지지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(VectorStore.class)
public class AiRagEtlServiceImpl implements AiRagEtlService {

    private static final String RAG_DOC_PATTERN = "classpath*:rag-docs/**/*.*";

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resourcePatternResolver;

    @Override
    public AiRagIndexResponseDto indexClasspathDocuments() {
        try {
            List<Document> sourceDocuments = loadClasspathDocuments();
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.apply(sourceDocuments);

            vectorStore.add(chunks);

            log.info("[AiRagEtl] RAG 문서 색인 완료. sourceDocuments={}, chunks={}",
                    sourceDocuments.size(), chunks.size());

            return new AiRagIndexResponseDto(
                    sourceDocuments.size(),
                    chunks.size(),
                    "RAG classpath 문서 색인이 완료되었습니다."
            );
        } catch (IOException e) {
            throw new IllegalStateException("RAG classpath 문서 색인에 실패했습니다.", e);
        }
    }

    @Override
    public AiRagIndexResponseDto clearByFeature(String feature) {
        vectorStore.delete("feature == '%s'".formatted(feature));
        return new AiRagIndexResponseDto(0, 0, "feature=%s RAG 문서를 삭제했습니다.".formatted(feature));
    }

    private List<Document> loadClasspathDocuments() throws IOException {
        Resource[] resources = resourcePatternResolver.getResources(RAG_DOC_PATTERN);
        List<Document> documents = new ArrayList<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !(filename.endsWith(".md") || filename.endsWith(".txt"))) {
                continue;
            }

            List<Document> readDocuments = new TextReader(resource).read();
            for (Document document : readDocuments) {
                enrichMetadata(document, resource);
                documents.add(document);
            }
        }

        return documents;
    }

    private void enrichMetadata(Document document, Resource resource) throws IOException {
        String sourcePath = resource.getURL().toString();
        String normalizedPath = sourcePath.replace("\\", "/");
        String feature = resolveFeature(normalizedPath);
        String filename = resource.getFilename() == null ? "unknown" : resource.getFilename();

        document.getMetadata().put("feature", feature);
        document.getMetadata().put("source", normalizedPath);
        document.getMetadata().put("title", filename);
        document.getMetadata().put("type", "POLICY_DOCUMENT");
    }

    private String resolveFeature(String sourcePath) {
        if (sourcePath.contains("/rag-docs/support/")) {
            return "SUPPORT";
        }
        if (sourcePath.contains("/rag-docs/report/")) {
            return "REPORT";
        }
        if (sourcePath.contains("/rag-docs/matching/")) {
            return "MATCHING";
        }

        return "GENERAL";
    }
}
