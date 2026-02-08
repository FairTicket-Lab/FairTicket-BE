package com.fairticket.domain.concert.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("concerts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Concert {

    @Id
    private Long id;

    private String title;

    private String artist;

    private String venue;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
