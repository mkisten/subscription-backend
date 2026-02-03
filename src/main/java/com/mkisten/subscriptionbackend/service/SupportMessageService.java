package com.mkisten.subscriptionbackend.service;

import com.mkisten.subscriptionbackend.dto.SupportMessageDto;
import com.mkisten.subscriptionbackend.entity.SupportMessage;
import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.repository.SupportMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportMessageService {

    private final SupportMessageRepository supportMessageRepository;
    private final UserService userService;

    public SupportMessage createMessage(Long telegramId, String message, String source) {
        SupportMessage supportMessage = new SupportMessage();
        supportMessage.setTelegramId(telegramId);
        supportMessage.setMessage(message);
        supportMessage.setSource(source);
        supportMessage.setStatus("NEW");
        supportMessage.setCreatedAt(LocalDateTime.now());
        return supportMessageRepository.save(supportMessage);
    }

    public List<SupportMessageDto> getUserMessages(Long telegramId) {
        return supportMessageRepository.findByTelegramIdOrderByCreatedAtDesc(telegramId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<SupportMessageDto> getAllMessages() {
        return supportMessageRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public SupportMessage replyMessage(Long id, String reply, Long adminTelegramId) {
        SupportMessage message = supportMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Support message not found"));
        message.setAdminReply(reply);
        message.setRepliedAt(LocalDateTime.now());
        message.setAdminTelegramId(adminTelegramId);
        message.setStatus("REPLIED");
        return supportMessageRepository.save(message);
    }

    @Transactional
    public int markReadForUser(Long telegramId) {
        return supportMessageRepository.markReadByTelegramId(telegramId);
    }

    private SupportMessageDto toDto(SupportMessage message) {
        SupportMessageDto dto = new SupportMessageDto();
        dto.setId(message.getId());
        dto.setTelegramId(message.getTelegramId());
        dto.setMessage(message.getMessage());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setSource(message.getSource());
        dto.setStatus(message.getStatus());
        dto.setAdminReply(message.getAdminReply());
        dto.setRepliedAt(message.getRepliedAt());
        dto.setAdminTelegramId(message.getAdminTelegramId());

        Optional<User> userOpt = userService.findByTelegramIdOptional(message.getTelegramId());
        userOpt.ifPresent(user -> {
            dto.setFirstName(user.getFirstName());
            dto.setLastName(user.getLastName());
            dto.setUsername(user.getUsername());
        });
        return dto;
    }
}
