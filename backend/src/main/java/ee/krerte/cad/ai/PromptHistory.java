package ee.krerte.cad.ai;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Iga edukalt resolveeritud prompt + template salvestatakse siia, et
 * {@link TemplateRagService} saaks järgmistele kasutajatele teha
 * <em>RAG-lite</em> similarity-soovituse.
 *
 * <p>Miks "lite"? Meil ei ole pgvector'i + embedding-model'i. Selle asemel
 * kasutame Postgresi enda {@code tsvector} + {@code pg_trgm} similarity'id —
 * eestikeelne tekst lühikestes kirjeldustes on nii tugev signaal, et seda
 * piisab 90% juhtudel. Kui korpus kasvab üle 100k kirje või lisanduvad mitu
 * keelt, migreerume päriselt pgvector + bge-m3 peale.
 *
 * <p>Params salvestame jsonb tekstina (String), järgides sama mustrit, mida
 * olemasolevad entity'd ({@code Design.params}) kasutavad — ilma hypersistence
 * dependency'ta.
 */
@Entity
@Table(name = "prompt_history")
public class PromptHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "prompt_et", nullable = false, columnDefinition = "text")
    private String promptEt;

    @Column(name = "template", nullable = false, length = 64)
    private String template;

    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    private String params;

    @Column(name = "downloaded", nullable = false)
    private boolean downloaded;

    @Column(name = "review_score")
    private Integer reviewScore;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public PromptHistory() {}

    public PromptHistory(Long userId, String promptEt, String template, String paramsJson) {
        this.userId = userId;
        this.promptEt = promptEt;
        this.template = template;
        this.params = paramsJson;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPromptEt() { return promptEt; }
    public String getTemplate() { return template; }
    public String getParams() { return params; }
    public boolean isDownloaded() { return downloaded; }
    public Integer getReviewScore() { return reviewScore; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setDownloaded(boolean downloaded) { this.downloaded = downloaded; }
    public void setReviewScore(Integer s) { this.reviewScore = s; }
}
