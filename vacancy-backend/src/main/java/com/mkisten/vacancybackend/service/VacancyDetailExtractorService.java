package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.entity.Vacancy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VacancyDetailExtractorService {

    @Value("${app.ai-resume.timeout-ms:90000}")
    private int timeoutMs;

    @Value("${app.ai-resume.user-agent:VacancyResumeAssistant/1.0}")
    private String userAgent;

    @Value("${app.ai-resume.max-vacancy-text-length:18000}")
    private int maxVacancyTextLength;

    public String buildVacancySnapshot(Vacancy vacancy) {
        StringBuilder builder = new StringBuilder();
        builder.append("Источник: ").append(safe(vacancy.getSource())).append('\n');
        builder.append("Название: ").append(safe(vacancy.getTitle())).append('\n');
        builder.append("Компания: ").append(safe(vacancy.getEmployer())).append('\n');
        builder.append("Город: ").append(safe(vacancy.getCity())).append('\n');
        builder.append("Формат: ").append(safe(vacancy.getSchedule())).append('\n');
        builder.append("Зарплата: ").append(safe(vacancy.getSalary())).append('\n');
        builder.append("Ссылка: ").append(safe(vacancy.getUrl())).append("\n\n");

        String pageText = fetchPageText(vacancy.getUrl());
        if (!pageText.isBlank()) {
            builder.append("Текст вакансии:\n").append(pageText);
        }

        String normalized = builder.toString().replace("\u0000", "").trim();
        return normalized.length() <= maxVacancyTextLength
                ? normalized
                : normalized.substring(0, maxVacancyTextLength);
    }

    private String fetchPageText(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            Document document = Jsoup.connect(url)
                    .timeout(timeoutMs)
                    .userAgent(userAgent)
                    .referrer("https://www.google.com/")
                    .followRedirects(true)
                    .get();

            StringBuilder builder = new StringBuilder();
            builder.append(document.title()).append("\n\n");

            Element metaDescription = document.selectFirst("meta[name=description], meta[property=og:description]");
            if (metaDescription != null) {
                builder.append(metaDescription.attr("content")).append("\n\n");
            }

            String text = document.body() != null ? document.body().text() : "";
            builder.append(text);
            return compact(builder.toString());
        } catch (Exception ex) {
            log.warn("Failed to load vacancy details from {}: {}", url, ex.getMessage());
            return "";
        }
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
