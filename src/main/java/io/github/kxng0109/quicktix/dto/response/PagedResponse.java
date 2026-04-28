package io.github.kxng0109.quicktix.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResponse<T>(
		List<T> content,
		int pageNumber,
		int pageSize,
		long totalElements,
		int totalPages,
		boolean isLast
) {
	// Helper method to effortlessly convert Spring's Page into our JSON-safe record
	public static <T> PagedResponse<T> from(Page<T> page) {
		return new PagedResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isLast()
		);
	}
}
