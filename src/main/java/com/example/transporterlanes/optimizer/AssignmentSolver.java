package com.example.transporterlanes.optimizer;

/**
 * Finds the minimum-cost assignment of transporters to lanes using at most {@code maxTransporters} transporters.
 *
 * <p>Rules every implementation must follow:
 * <ol>
 *   <li>Every lane is assigned to exactly one transporter from the chosen combination that quotes it.</li>
 *   <li>Within a combination, each lane goes to the lowest quote; equal quotes go to the lower transporter id.</li>
 *   <li>Among feasible combinations of size 1..K, pick the lowest total cost; then the fewest transporters; then the
 *       lexicographically smallest ascending list of transporter ids.</li>
 *   <li>K larger than the number of transporters is capped to that number.</li>
 * </ol>
 */
public interface AssignmentSolver {

    /**
     * @throws NoFeasibleAssignmentException if no combination of at most K transporters covers every lane
     * @throws ProblemTooLargeException      if the search would exceed the solver's configured work limit
     */
    AssignmentSolution solve(OptimizationProblem problem);
}
