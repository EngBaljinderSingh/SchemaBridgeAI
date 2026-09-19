package com.schemabridge.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "synonym_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SynonymEntry {

    @Id
    @Column(length = 64)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String domain = "GENERAL";

    @Column(name = "canonical_term", nullable = false)
    private String canonicalTerm;

    @Column(nullable = false)
    private String synonym;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
