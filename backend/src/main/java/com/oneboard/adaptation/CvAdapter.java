package com.oneboard.adaptation;

import java.util.List;

import com.oneboard.adaptation.SkillExtractor.SkillMatch;
import com.oneboard.cv.Cv;
import com.oneboard.vacancy.Vacancy;

/** Strategy that rewrites a base CV for a specific vacancy. */
public interface CvAdapter {

    /** "keyword", "claude", ... */
    String id();

    /** Whether this adapter can be used right now (e.g. API key configured). */
    boolean available();

    Result adapt(Cv cv, Vacancy vacancy, SkillMatch match);

    /**
     * @param content adapted CV (Markdown / plain text)
     * @param model   model or algorithm that produced it
     * @param notes   advice for the candidate: what changed, what gaps to close honestly
     */
    record Result(String content, String model, List<String> notes) {}
}
