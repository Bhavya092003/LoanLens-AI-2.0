package com.lending.platform.policyqa;

import com.lending.platform.common.api.ApiException;
import com.lending.platform.policydocument.PolicyChunk;
import com.lending.platform.policydocument.PolicyChunkRepository;
import com.lending.platform.policydocument.PolicyDocument;
import com.lending.platform.policydocument.PolicyDocumentPage;
import com.lending.platform.policydocument.PolicyDocumentPageRepository;
import com.lending.platform.policydocument.PolicyDocumentRepository;
import com.lending.platform.policydocument.PolicyDocumentStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyVectorService {
    private final PolicyDocumentRepository documents;
    private final PolicyDocumentPageRepository pages;
    private final PolicyChunkRepository chunks;
    private final PolicyChunkingService chunker;
    private final EmbeddingProvider embeddings;
    private final JdbcTemplate jdbc;
    private final int dimensions;
    private final int topK;
    private final double minimumSimilarity;

    public PolicyVectorService(PolicyDocumentRepository documents, PolicyDocumentPageRepository pages,
                               PolicyChunkRepository chunks, PolicyChunkingService chunker,
                               EmbeddingProvider embeddings, JdbcTemplate jdbc,
                               @Value("${app.ai.embedding-dimensions:1536}") int dimensions,
                               @Value("${app.policy-retrieval.top-k:5}") int topK,
                               @Value("${app.policy-retrieval.minimum-similarity:0.25}") double minimumSimilarity) {
        this.documents = documents; this.pages = pages; this.chunks = chunks; this.chunker = chunker;
        this.embeddings = embeddings; this.jdbc = jdbc; this.dimensions = dimensions; this.topK = topK;
        this.minimumSimilarity = minimumSimilarity;
    }

    @Transactional
    public void index(UUID documentId) {
        PolicyDocument document = documents.findById(documentId).orElseThrow();
        List<PolicyDocumentPage> sourcePages = pages.findByPolicyDocument_IdOrderByPageNumber(documentId);
        List<PolicyChunkingService.Chunk> pieces = chunker.chunk(sourcePages);
        if (pieces.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_POLICY_TEXT", "The policy has no extractable text to index");
        List<List<Double>> vectors = embeddings.embedAll(pieces.stream().map(PolicyChunkingService.Chunk::text).toList());
        if (vectors == null || vectors.size() != pieces.size() || vectors.stream().anyMatch(this::invalidEmbedding)) {
            throw new AiProviderException("Embedding provider returned vectors with invalid dimensions or values");
        }
        chunks.deleteAllByPolicyDocument_Id(documentId);
        List<PolicyChunk> entities = pieces.stream().map(piece -> new PolicyChunk(document, piece.index(), null,
                piece.text(), piece.page(), Map.of("pages", List.of(piece.page()), "documentVersion", document.getVersion()))).toList();
        List<PolicyChunk> saved = chunks.saveAllAndFlush(entities);
        for (int i = 0; i < saved.size(); i++) {
            jdbc.update("UPDATE policy_chunks SET embedding = ?::vector WHERE id = ?", vectorLiteral(vectors.get(i)), saved.get(i).getId());
        }
        document.completeExtraction(PolicyDocumentStatus.INDEXED);
    }

    private List<Double> validatedEmbedding(String text) {
        List<Double> vector = embeddings.embed(text);
        if (invalidEmbedding(vector)) {
            throw new AiProviderException("Embedding provider returned a vector with an invalid dimension or value");
        }
        return vector;
    }

    private boolean invalidEmbedding(List<Double> vector) {
        return vector == null || vector.size() != dimensions || vector.stream().anyMatch(v -> v == null || !Double.isFinite(v));
    }

    public List<Evidence> retrieve(String question, String documentType, String version) {
        List<Double> vector = validatedEmbedding(question);
        String sql = "SELECT c.id, c.chunk_text, c.section_title, c.page_number, d.id document_id, d.document_name, d.version, d.effective_date, 1 - (c.embedding <=> ?::vector) similarity "
                + "FROM policy_chunks c JOIN policy_documents d ON d.id=c.policy_document_id "
                + "WHERE c.embedding IS NOT NULL AND d.status='INDEXED' AND (CAST(? AS varchar) IS NULL OR lower(d.document_type)=lower(CAST(? AS varchar))) AND (CAST(? AS varchar) IS NULL OR d.version=CAST(? AS varchar)) "
                + "ORDER BY c.embedding <=> ?::vector LIMIT ?";
        String literal = vectorLiteral(vector);
        return jdbc.query(sql, (rs, row) -> new Evidence(rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class), rs.getString("document_name"), rs.getString("version"),
                rs.getDate("effective_date") == null ? null : rs.getDate("effective_date").toLocalDate(),
                rs.getString("section_title"), rs.getInt("page_number"), rs.getString("chunk_text"), rs.getDouble("similarity")),
                literal, documentType, documentType, version, version, literal, topK)
                .stream().filter(item -> item.similarity() >= minimumSimilarity).toList();
    }

    private String vectorLiteral(List<Double> vector) { return "[" + String.join(",", vector.stream().map(String::valueOf).toList()) + "]"; }
    public record Evidence(UUID chunkId, UUID documentId, String document, String version, LocalDate effectiveDate,
                           String section, int page, String text, double similarity) {}
}
