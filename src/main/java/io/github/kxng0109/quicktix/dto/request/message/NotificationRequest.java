package io.github.kxng0109.quicktix.dto.request.message;

import lombok.Builder;

import java.util.List;

@Builder
public record NotificationRequest(
		List<String> to,
		String subject,
		String body,
		String htmlBody,
		List<AttachmentRequest> attachmentRequests
) {
}
