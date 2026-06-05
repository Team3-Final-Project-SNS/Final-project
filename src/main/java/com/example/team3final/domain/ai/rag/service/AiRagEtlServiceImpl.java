package com.example.team3final.domain.ai.rag.service;

import com.example.team3final.domain.ai.rag.dto.AiRagIndexResponseDto;
import com.example.team3final.domain.ai.rag.entity.AiRagDocumentIndex;
import com.example.team3final.domain.ai.rag.repository.AiRagDocumentIndexRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * classpath 문서를 대상으로 하는 RAG ETL 파이프라인 구현체입니다.
 *
 * 현재 단계에서는 src/main/resources/rag-docs 아래의 .md/.txt 문서를 읽어
 * Spring AI Document로 변환하고, TokenTextSplitter로 chunk를 나눈 뒤
 * VectorStore에 저장합니다. pgvector를 사용하면 Spring AI가 embedding 생성과
 * vector_store 저장을 처리합니다.
 *
 * 각 문서의 content hash를 MySQL의 AiRagDocumentIndex에 기록하여
 * 서버 재시작 시 변경되지 않은 문서는 다시 embedding하지 않습니다.
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
    private final AiRagDocumentIndexRepository aiRagDocumentIndexRepository;

    @Override
    @Transactional
    public AiRagIndexResponseDto indexClasspathDocuments() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(RAG_DOC_PATTERN);
            int sourceDocumentCount = 0;
            int indexedChunkCount = 0;
            int skippedDocumentCount = 0;

            for (Resource resource : resources) {
                if (!isSupportedDocument(resource)) {
                    continue;
                }

                sourceDocumentCount++;
                IndexResult result = indexResourceIfChanged(resource);
                indexedChunkCount += result.indexedChunkCount();
                skippedDocumentCount += result.skipped() ? 1 : 0;
            }

            log.info("[AiRagEtl] RAG 문서 색인 완료. sourceDocuments={}, indexedChunks={}, skippedDocuments={}",
                    sourceDocumentCount, indexedChunkCount, skippedDocumentCount);

            return new AiRagIndexResponseDto(
                    sourceDocumentCount,
                    indexedChunkCount,
                    "RAG classpath 문서 색인이 완료되었습니다. skippedDocuments=%d".formatted(skippedDocumentCount)
            );
        } catch (IOException e) {
            throw new IllegalStateException("RAG classpath 문서 색인에 실패했습니다.", e);
        }
    }

    @Override
    @Transactional
    public AiRagIndexResponseDto clearByFeature(String feature) {
        vectorStore.delete("feature == '%s'".formatted(feature));
        aiRagDocumentIndexRepository.deleteAllByFeature(feature);
        return new AiRagIndexResponseDto(0, 0, "feature=%s RAG 문서를 삭제했습니다.".formatted(feature));
    }

    private IndexResult indexResourceIfChanged(Resource resource) throws IOException {
        String source = resolveSource(resource);
        String feature = resolveFeature(source);
        String contentHash = contentHash(resource);

        Optional<AiRagDocumentIndex> existingIndex = aiRagDocumentIndexRepository.findBySource(source);
        if (existingIndex.isPresent() && existingIndex.get().hasSameHash(contentHash)) {
            log.debug("[AiRagEtl] 변경 없는 RAG 문서 skip. source={}", source);
            return IndexResult.skippedResult();
        }

        vectorStore.delete("source == '%s'".formatted(escapeFilterValue(source)));

        List<Document> sourceDocuments = new TextReader(resource).read();
        for (Document document : sourceDocuments) {
            enrichMetadata(document, resource, source, feature);
        }

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(sourceDocuments);

        for (Document chunk : chunks) {
            chunk.getMetadata().put("source", source);
            chunk.getMetadata().put("feature", feature);
        }

        vectorStore.add(chunks);

        AiRagDocumentIndex documentIndex = existingIndex.orElseGet(() -> AiRagDocumentIndex.builder()
                .source(source)
                .feature(feature)
                .contentHash(contentHash)
                .chunkCount(chunks.size())
                .indexedAt(LocalDateTime.now())
                .build());

        documentIndex.updateIndex(feature, contentHash, chunks.size());
        aiRagDocumentIndexRepository.save(documentIndex);

        log.info("[AiRagEtl] RAG 문서 색인. source={}, feature={}, chunks={}", source, feature, chunks.size());
        return IndexResult.indexed(chunks.size());
    }

    private void enrichMetadata(Document document, Resource resource, String source, String feature) {
        String filename = resource.getFilename() == null ? "unknown" : resource.getFilename();

        document.getMetadata().put("feature", feature);
        document.getMetadata().put("source", source);
        document.getMetadata().put("title", filename);
        document.getMetadata().put("type", "POLICY_DOCUMENT");
    }

    private String resolveFeature(String sourcePath) {
        if (sourcePath.startsWith("support/")) {
            return "SUPPORT";
        }
        if (sourcePath.startsWith("report/")) {
            return "REPORT";
        }
        if (sourcePath.startsWith("matching/")) {
            return "MATCHING";
        }

        return "GENERAL";
    }

    private boolean isSupportedDocument(Resource resource) {
        String filename = resource.getFilename();
        return filename != null && (filename.endsWith(".md") || filename.endsWith(".txt"));
    }

    private String resolveSource(Resource resource) throws IOException {
        String normalizedPath = resource.getURL().toString().replace("\\", "/");
        int index = normalizedPath.indexOf("/rag-docs/");
        return index >= 0 ? normalizedPath.substring(index + "/rag-docs/".length()) : normalizedPath;
    }

    private String contentHash(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return DigestUtils.md5DigestAsHex(inputStream);
        }
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }

    private record IndexResult(
            int indexedChunkCount,
            boolean skipped
    ) {
        private static IndexResult indexed(int indexedChunkCount) {
            return new IndexResult(indexedChunkCount, false);
        }

        private static IndexResult skippedResult() {
            return new IndexResult(0, true);
        }
    }
}
