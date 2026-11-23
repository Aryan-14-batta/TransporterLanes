package com.example.transporterlanes.service;

import com.example.transporterlanes.dto.ErrorResponse.FieldError;
import com.example.transporterlanes.dto.InputRequest;
import com.example.transporterlanes.dto.LaneQuoteRequest;
import com.example.transporterlanes.dto.TransporterRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Rules that span the whole request and so cannot be expressed as Bean Validation annotations on single fields.
 * Assumes Bean Validation has already passed (no nulls, positive ids and quotes). Reports every problem, not just
 * the first.
 */
@Component
public class InputDataValidator {

    public List<FieldError> validate(InputRequest request) {
        List<FieldError> errors = new ArrayList<>();

        Map<Long, Integer> laneIndexById = new HashMap<>();
        for (int i = 0; i < request.lanes().size(); i++) {
            Long id = request.lanes().get(i).id();
            Integer first = laneIndexById.putIfAbsent(id, i);
            if (first != null) {
                errors.add(new FieldError("lanes[" + i + "].id",
                        "Duplicate lane id " + id + " (already used by lanes[" + first + "])"));
            }
        }

        Map<Long, Integer> transporterIndexById = new HashMap<>();
        Set<Long> quotedLanes = new HashSet<>();
        for (int t = 0; t < request.transporters().size(); t++) {
            TransporterRequest transporter = request.transporters().get(t);
            Integer first = transporterIndexById.putIfAbsent(transporter.id(), t);
            if (first != null) {
                errors.add(new FieldError("transporters[" + t + "].id",
                        "Duplicate transporter id " + transporter.id() + " (already used by transporters[" + first + "])"));
            }

            Set<Long> lanesQuotedByThisTransporter = new HashSet<>();
            for (int q = 0; q < transporter.laneQuotes().size(); q++) {
                LaneQuoteRequest quote = transporter.laneQuotes().get(q);
                String field = "transporters[" + t + "].laneQuotes[" + q + "].laneId";
                if (!laneIndexById.containsKey(quote.laneId())) {
                    errors.add(new FieldError(field, "Unknown lane id " + quote.laneId()));
                } else if (!lanesQuotedByThisTransporter.add(quote.laneId())) {
                    errors.add(new FieldError(field, "Duplicate quote for lane " + quote.laneId()));
                } else {
                    quotedLanes.add(quote.laneId());
                }
            }
        }

        for (int i = 0; i < request.lanes().size(); i++) {
            Long id = request.lanes().get(i).id();
            // Only report each lane id once, at its first occurrence.
            if (laneIndexById.get(id) == i && !quotedLanes.contains(id)) {
                errors.add(new FieldError("lanes[" + i + "]",
                        "Lane " + id + " has no quotes; every lane must be quoted by at least one transporter"));
            }
        }
        return errors;
    }
}
