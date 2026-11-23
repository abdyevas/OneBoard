package com.oneboard.adaptation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.oneboard.adaptation.SkillExtractor.SkillMatch;
import com.oneboard.cv.Cv;
import com.oneboard.vacancy.Vacancy;

/**
 * Offline adapter: no LLM. It never invents anything — it only surfaces the CV's own skills that the vacancy
 * asks for in a "Key skills" block at the top and tells the candidate which requirements are not covered.
 */
@Component
public class KeywordCvAdapter implements CvAdapter {

    @Override public String id() { return "keyword"; }
    @Override public boolean available() { return true; }

    @Override
    public Result adapt(Cv cv, Vacancy vacancy, SkillMatch match) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- Adapted for: ").append(vacancy.getTitle());
        if (vacancy.getCompany() != null) sb.append(" @ ").append(vacancy.getCompany());
        sb.append(" -->\n\n");

        if (!match.matched().isEmpty()) {
            sb.append("## Key skills for this role\n\n");
            sb.append(String.join(" · ", match.matched())).append("\n\n");
            if (!match.extraCvSkills().isEmpty()) {
                sb.append("Also: ").append(String.join(", ", match.extraCvSkills())).append("\n\n");
            }
            sb.append("---\n\n");
        }
        sb.append(cv.getContent().strip()).append('\n');

        List<String> notes = new ArrayList<>();
        notes.add("Совпадение по навыкам: " + match.score() + "% ("
                + match.matched().size() + " из " + match.vacancySkills().size() + ").");
        if (!match.matched().isEmpty()) {
            notes.add("Навыки из вакансии, которые есть в CV, вынесены в начало: " + String.join(", ", match.matched()) + ".");
        }
        for (String s : match.missing()) {
            notes.add("В вакансии упоминается «" + s + "», в CV не найдено. Если опыт есть — опишите его явно (проект, задача, результат).");
        }
        if (match.vacancySkills().isEmpty()) {
            notes.add("В описании вакансии не найдено известных навыков — проверьте требования вручную.");
        } else if (match.score() < 40) {
            notes.add("Низкое совпадение: возможно, вакансия не ваш профиль, либо CV стоит дополнить.");
        }
        notes.add("Для переписывания формулировок под вакансию настройте ANTHROPIC_API_KEY и выберите метод «claude».");
        return new Result(sb.toString(), "keyword-v1", notes);
    }
}
