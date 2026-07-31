package com.mkisten.vacancybackend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Table(name="user_settings_audit", indexes={@Index(name="idx_settings_audit_target",columnList="target_telegram_id"),@Index(name="idx_settings_audit_changed_at",columnList="changed_at")})
@Getter @Setter public class UserSettingsAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="target_telegram_id",nullable=false) private Long targetTelegramId;
 @Column(name="actor_telegram_id") private Long actorTelegramId;
 @Column(name="field_name",nullable=false,length=100) private String fieldName;
 @Column(name="old_value",columnDefinition="TEXT") private String oldValue;
 @Column(name="new_value",columnDefinition="TEXT") private String newValue;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
}
