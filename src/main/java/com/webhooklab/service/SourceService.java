package com.webhooklab.service;

import com.webhooklab.dto.CreateSourceRequest;
import com.webhooklab.dto.SourceResponse;
import com.webhooklab.entity.User;
import com.webhooklab.entity.WebhookSource;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.repository.WebhookSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceService {

    private final WebhookSourceRepository webhookSourceRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public SourceResponse createSource(CreateSourceRequest request, String userIdentifier) {
        User user = findUser(userIdentifier);

        WebhookSource source = new WebhookSource();
        source.setName(request.name());
        source.setSecret(generateSecret());
        source.setOwner(user);

        WebhookSource saved = webhookSourceRepository.save(source);

        return new SourceResponse(saved.getId(), saved.getName(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> getSources(String userIdentifier) {
        User user = findUser(userIdentifier);

        List<WebhookSource> sources = webhookSourceRepository.findByOwnerId(user.getId());

        return sources.stream()
                .map(s -> new SourceResponse(s.getId(), s.getName(), s.getCreatedAt()))
                .toList();
    }

    private User findUser(String identifier) {
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
