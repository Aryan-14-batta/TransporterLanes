package com.example.transporterlanes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "A transporter's price for one lane.")
public record LaneQuoteRequest(
        @Schema(description = "Id of a lane in the same request.", example = "1", minimum = "1")
        @NotNull @Positive Long laneId,
        @Schema(description = "Price in whole rupees (integer; decimals are rejected), 1 to 1,000,000,000,000.",
                example = "5000", minimum = "1")
        @NotNull @Positive @Max(LaneQuoteRequest.MAX_QUOTE) Long quote) {

    /** 1 lakh crore rupees (10^12). Keeps realistic totals far from long overflow. */
    public static final long MAX_QUOTE = 1_000_000_000_000L;
}
