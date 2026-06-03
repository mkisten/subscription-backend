package com.mkisten.vacancybackend.dto;

import com.mkisten.vacancybackend.entity.ResumeProfile;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResumeProfileResponse {
    private Long id;
    private String fileName;
    private String contentType;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ResumeProfileResponse(ResumeProfile profile) {
        this.id = profile.getId();
        this.fileName = profile.getFileName();
        this.contentType = profile.getContentType();
        this.active = profile.getActive();
        this.createdAt = profile.getCreatedAt();
        this.updatedAt = profile.getUpdatedAt();
    }
}
