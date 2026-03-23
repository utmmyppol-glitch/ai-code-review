package com.bomin.codereview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubApiService {

    private final ObjectMapper objectMapper;

    @Value("${github.token}")
    private String githubToken;

    private static final String GITHUB_API = "https://api.github.com";

    /**
     * PR의 diff(변경 코드) 조회
     */
    public String getPrDiff(String repository, int prNumber) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);
            headers.setAccept(List.of(MediaType.valueOf("application/vnd.github.v3.diff")));

            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + repository + "/pulls/" + prNumber,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("PR diff 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * PR의 변경된 파일 목록 조회
     */
    public JsonNode getPrFiles(String repository, int prNumber) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + repository + "/pulls/" + prNumber + "/files",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("PR 파일 목록 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * PR에 리뷰 코멘트 작성
     */
    public boolean postReviewComment(String repository, int prNumber, String body) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "body", body,
                    "event", "COMMENT"
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + repository + "/pulls/" + prNumber + "/reviews",
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("GitHub 코멘트 작성 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * PR에 일반 코멘트 (Issue Comment) 작성
     */
    public boolean postIssueComment(String repository, int prNumber, String body) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = Map.of("body", body);

            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/repos/" + repository + "/issues/" + prNumber + "/comments",
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("GitHub 이슈 코멘트 작성 실패: {}", e.getMessage());
            return false;
        }
    }
}
