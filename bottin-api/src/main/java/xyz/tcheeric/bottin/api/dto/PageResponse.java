package xyz.tcheeric.bottin.api.dto;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Generic paginated response wrapper.
 *
 * @param <T> the type of content
 */
@Value
@Builder
public class PageResponse<T> {

    List<T> content;
    int page;
    int size;
    long totalElements;
    int totalPages;
    boolean first;
    boolean last;
    boolean hasNext;
    boolean hasPrevious;

    /**
     * Creates a PageResponse from a Spring Data Page.
     *
     * @param page   the Spring Data Page
     * @param mapper function to convert entity to DTO
     * @param <E>    entity type
     * @param <D>    DTO type
     * @return PageResponse containing mapped content
     */
    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        return PageResponse.<D>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
