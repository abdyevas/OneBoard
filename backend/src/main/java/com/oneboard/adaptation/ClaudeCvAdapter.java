package com.oneboard.adaptation;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlock;
import com.oneboard.adaptation.SkillExtractor.SkillMatch;
import com.oneboard.cv.Cv;
import com.oneboard.vacancy.Vacancy;

/** Rewrites the CV for a vacancy with Claude. Enabled when {@code ANTHROPIC_API_KEY} is set. */
@Component
public class ClaudeCvAdapter implements CvAdapter {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCvAdapter.class);
    private static final String NOTES_MARKER = "===NOTES===";

    private static final String SYSTEM_PROMPT = """
            You are an expert technical recruiter and CV editor. You tailor a candidate's existing CV to a specific \
            job vacancy so that the most relevant experience is easy to spot for a recruiter and for ATS keyword filters.

            Hard rules:
            - Never invent facts. Do not add employers, job titles, dates, degrees, certifications, metrics, projects \
            or skills that are not supported by the original CV. Rephrasing, reordering, condensing and emphasising \
            existing content is allowed; fabricating is not.
            - Where the vacancy uses a different term for something the candidate clearly did, you may use the \
            vacancy's wording.
            - Keep the CV in the same language as the original CV.
            - Keep all contact details and the factual timeline intact.
            - Put a short professional summary (2-4 sentences) aimed at this role at the top, then the sections in the \
            order that best serves this vacancy. Drop or shorten content that is irrelevant to the role.

            Output format:
            1. The full adapted CV in Markdown, nothing before it.
            2. Then a line containing exactly ===NOTES===
            3. Then 3-8 short bullet points in Russian for the candidate: what you changed and why, and which \
            requirements of the vacancy are not covered by the CV (so the candidate can address them honestly). \
            Each bullet on its own line starting with "- ".
            """;

    private final ClaudeProperties props;
    private volatile AnthropicClient client;

    public ClaudeCvAdapter(ClaudeProperties props) {
        this.props = props;
    }

    @Override public String id() { return "claude"; }

    @Override
    public boolean available() {
        return props.configured();
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build();
                }
            }
        }
        return client;
    }

    @Override
    public Result adapt(Cv cv, Vacancy vacancy, SkillMatch match) {
        if (!available()) {
            throw new IllegalStateException("Claude adapter is not configured: set the ANTHROPIC_API_KEY environment variable");
        }
        MessageCreateParams params = MessageCreateParams.builder()
                .model(props.model())
                .maxTokens(props.maxTokens())
                .system(SYSTEM_PROMPT)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                .addUserMessage(userPrompt(cv, vacancy, match))
                // If the primary model declines, let the API retry on a fallback model server-side.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();

        Message response;
        try {
            response = client().messages().create(params);
        } catch (AnthropicServiceException e) {
            log.warn("Claude request failed: {}", e.getMessage());
            throw new IllegalStateException("Claude API error: " + e.getMessage(), e);
        }

        String stopReason = response.stopReason().map(Object::toString).orElse("");
        if ("refusal".equals(stopReason)) {
            throw new IllegalStateException("Claude declined to process this CV/vacancy");
        }

        String text = response.content().stream()
                .flatMap(b -> b.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        if (text.isBlank()) {
            throw new IllegalStateException("Claude returned an empty response (stop reason: " + stopReason + ")");
        }

        String content = text;
        List<String> notes = List.of();
        int idx = text.indexOf(NOTES_MARKER);
        if (idx >= 0) {
            content = text.substring(0, idx);
            notes = Arrays.stream(text.substring(idx + NOTES_MARKER.length()).split("\\R"))
                    .map(String::strip)
                    .filter(l -> !l.isEmpty())
                    .map(l -> l.replaceFirst("^[-*•]\\s*", ""))
                    .toList();
        }
        if ("max_tokens".equals(stopReason)) {
            notes = new java.util.ArrayList<>(notes);
            notes.add("Ответ модели обрезан по лимиту токенов — увеличьте oneboard.claude.max-tokens.");
        }
        return new Result(content.strip() + "\n", response.model().asString(), notes);
    }

    private static String userPrompt(Cv cv, Vacancy v, SkillMatch match) {
        StringBuilder sb = new StringBuilder();
        sb.append("<vacancy>\n");
        sb.append("Title: ").append(v.getTitle()).append('\n');
        if (v.getCompany() != null) sb.append("Company: ").append(v.getCompany()).append('\n');
        if (v.getLocation() != null) sb.append("Location: ").append(v.getLocation()).append(v.isRemote() ? " (remote)" : "").append('\n');
        if (!v.getTags().isEmpty()) sb.append("Tags: ").append(String.join(", ", v.getTags())).append('\n');
        sb.append("\n").append(v.getDescription() == null ? "" : v.getDescription()).append("\n</vacancy>\n\n");

        sb.append("<original_cv>\n").append(cv.getContent()).append("\n</original_cv>\n\n");

        sb.append("<keyword_analysis>\n");
        sb.append("Skills required by the vacancy and present in the CV: ").append(join(match.matched())).append('\n');
        sb.append("Skills required by the vacancy but NOT found in the CV (do not add them unless the CV supports them): ")
                .append(join(match.missing())).append('\n');
        sb.append("</keyword_analysis>\n\n");
        sb.append("Tailor the CV to this vacancy following the rules.");
        return sb.toString();
    }

    private static String join(List<String> items) {
        return items.isEmpty() ? "(none)" : String.join(", ", items);
    }
}
