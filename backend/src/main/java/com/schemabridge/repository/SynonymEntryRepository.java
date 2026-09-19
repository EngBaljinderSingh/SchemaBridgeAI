package com.schemabridge.repository;

import com.schemabridge.domain.SynonymEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SynonymEntryRepository extends JpaRepository<SynonymEntry, String> {
    List<SynonymEntry> findByEnabledTrue();

    @Query("SELECT s FROM SynonymEntry s WHERE s.enabled = true AND " +
           "(LOWER(s.canonicalTerm) = LOWER(:term) OR LOWER(s.synonym) = LOWER(:term))")
    List<SynonymEntry> findMatchesForTerm(String term);
}
