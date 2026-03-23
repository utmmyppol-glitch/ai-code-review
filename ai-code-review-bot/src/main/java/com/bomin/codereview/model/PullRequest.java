package com.bomin.codereview.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pull_requests")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long githubPrId;

    @Column(nullable = false)
    private Integer prNumber;

    @Column(nullable = false)
    private String repository; // "owner/repo"

    @Column(nullable = false)
    private String title;

    @Column(length = 5000)
    private String description;

    @Column(nullable = false)
    private String author;

    private String branch;
    private String baseBranch;

    private Integer filesChanged;
    private Integer additions;
    private Integer deletions;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(length = 3000)
    private String aiSummary; // AI 한글 요약

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @OneToMany(mappedBy = "pullRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ReviewComment> comments = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    public enum RiskLevel {
        LOW,    // 🟢 낮음
        MEDIUM, // 🟡 중간
        HIGH    // 🔴 높음
    }

    public enum ReviewStatus {
        PENDING,    // 리뷰 대기
        REVIEWING,  // AI 분석 중
        COMPLETED,  // 리뷰 완료
        FAILED      // 분석 실패
    }
}
