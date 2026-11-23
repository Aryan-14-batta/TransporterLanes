package com.example.transporterlanes.controller;

/**
 * JSON examples shown in Swagger UI. Kept as constants so tests can verify that the success examples are exactly
 * what the API returns.
 *
 * <p>The example dataset has three lanes and three transporters (T3 does not serve lane 1, T1 does not serve lane 3).
 * With maxTransporters = 2, {T1, T3} and {T2, T3} both cost 11,000 with two transporters each, so the tie goes to the
 * lexicographically smaller id list [1, 3].
 */
public final class ApiExamples {

    private ApiExamples() {
    }

    public static final String INPUT_REQUEST = """
            {
              "lanes": [
                {"id": 1, "origin": "Mumbai", "destination": "Delhi"},
                {"id": 2, "origin": "Delhi", "destination": "Kolkata"},
                {"id": 3, "origin": "Chennai", "destination": "Bengaluru"}
              ],
              "transporters": [
                {"id": 1, "name": "Transporter T1", "laneQuotes": [
                  {"laneId": 1, "quote": 4000}, {"laneId": 2, "quote": 9000}]},
                {"id": 2, "name": "Transporter T2", "laneQuotes": [
                  {"laneId": 1, "quote": 6000}, {"laneId": 2, "quote": 3000}, {"laneId": 3, "quote": 8000}]},
                {"id": 3, "name": "Transporter T3", "laneQuotes": [
                  {"laneId": 2, "quote": 5000}, {"laneId": 3, "quote": 2000}]}
              ]
            }""";

    public static final String INPUT_SUCCESS = """
            {"status": "success", "message": "Input data saved successfully."}""";

    public static final String ASSIGNMENT_REQUEST = """
            {"maxTransporters": 2}""";

    public static final String ASSIGNMENT_SUCCESS = """
            {
              "status": "success",
              "totalCost": 11000,
              "assignments": [
                {"laneId": 1, "transporterId": 1},
                {"laneId": 2, "transporterId": 3},
                {"laneId": 3, "transporterId": 3}
              ],
              "selectedTransporters": [1, 3]
            }""";

    public static final String BEAN_VALIDATION_ERROR = """
            {
              "status": "error",
              "message": "Request validation failed.",
              "errors": [
                {"field": "lanes[0].origin", "message": "must not be blank"},
                {"field": "transporters[0].laneQuotes[0].quote", "message": "must be greater than 0"}
              ]
            }""";

    public static final String CROSS_FIELD_ERROR = """
            {
              "status": "error",
              "message": "Input data is invalid.",
              "errors": [
                {"field": "transporters[0].laneQuotes[1].laneId", "message": "Unknown lane id 99"},
                {"field": "lanes[2]",
                 "message": "Lane 3 has no quotes; every lane must be quoted by at least one transporter"}
              ]
            }""";

    public static final String MALFORMED_BODY_ERROR = """
            {
              "status": "error",
              "message": "Malformed request body.",
              "errors": [{"field": "transporters[0].laneQuotes[0].quote", "message": "Invalid value or type"}]
            }""";

    public static final String MAX_TRANSPORTERS_ERROR = """
            {
              "status": "error",
              "message": "Request validation failed.",
              "errors": [{"field": "maxTransporters", "message": "must be greater than or equal to 1"}]
            }""";

    public static final String NO_INPUT_DATA_ERROR = """
            {
              "status": "error",
              "message": "No input data found. Submit lanes and transporters to /api/v1/transporters/input first.",
              "errors": []
            }""";

    public static final String CONFLICT_ERROR = """
            {
              "status": "error",
              "message": "The dataset was modified by a concurrent request. Please retry.",
              "errors": []
            }""";

    public static final String INFEASIBLE_ERROR = """
            {
              "status": "error",
              "message": "No combination of at most 1 transporter(s) covers all 2 lanes.",
              "errors": []
            }""";

    public static final String TOO_LARGE_ERROR = """
            {
              "status": "error",
              "message": "Search space too large: 104857500 lane evaluations needed (combinations of up to 20 transporter(s) x 100 lane(s)), limit is 100000000. Reduce maxTransporters or the number of transporters.",
              "errors": []
            }""";

    public static final String OVERFLOW_ERROR = """
            {
              "status": "error",
              "message": "Every feasible combination of at most 1 transporter(s) has a total cost above 9223372036854775807, which cannot be represented.",
              "errors": []
            }""";

    public static final String UNEXPECTED_ERROR = """
            {"status": "error", "message": "An unexpected error occurred.", "errors": []}""";
}
