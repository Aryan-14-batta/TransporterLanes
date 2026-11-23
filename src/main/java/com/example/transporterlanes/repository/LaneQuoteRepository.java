package com.example.transporterlanes.repository;

import com.example.transporterlanes.domain.LaneQuote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LaneQuoteRepository extends JpaRepository<LaneQuote, Long> {

    /** Reads the foreign-key columns directly; no join and no entity loading. */
    @Query("select new com.example.transporterlanes.repository.QuoteRow(q.lane.id, q.transporter.id, q.quote) "
            + "from LaneQuote q")
    List<QuoteRow> findAllQuoteRows();
}
