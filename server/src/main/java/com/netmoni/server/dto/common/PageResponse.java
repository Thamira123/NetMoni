package com.netmoni.server.dto.common;
import java.util.List;
import org.springframework.data.domain.Page;
public record PageResponse<T>(int page,int size,long totalElements,int totalPages,List<T> content){
	public static <T> PageResponse<T> from(Page<T> source){return new PageResponse<>(source.getNumber(),source.getSize(),source.getTotalElements(),source.getTotalPages(),source.getContent());}
}
