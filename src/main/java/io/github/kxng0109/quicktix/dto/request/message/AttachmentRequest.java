package io.github.kxng0109.quicktix.dto.request.message;

import lombok.Builder;

@Builder
public record AttachmentRequest(
		String filename,
		String contentType,
		String data // Base64 encoded string
) {
}
