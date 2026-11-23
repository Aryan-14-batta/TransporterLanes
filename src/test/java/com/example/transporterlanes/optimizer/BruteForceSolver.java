package com.example.transporterlanes.optimizer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test-only reference solver: a literal, deliberately naive transcription of the optimization rules.
 * It shares no algorithmic code with {@link ExactCombinationSolver}; it walks subsets as bitmasks, evaluates each
 * one from scratch with exact {@link BigInteger} totals (so it cannot overflow) and picks the winner with an explicit
 * comparator. Only suitable for small inputs.
 */
final class BruteForceSolver implements AssignmentSolver {

    record Candidate(BigInteger cost, List<Long> members, List<LaneAssignment> assignments) {
    }

    static final Comparator<Candidate> PREFERENCE = Comparator
            .comparing(Candidate::cost)
            .thenComparingInt(candidate -> candidate.members().size())
            .thenComparing(Candidate::members, BruteForceSolver::compareLexicographically);

    @Override
    public AssignmentSolution solve(OptimizationProblem problem) {
        List<Long> lanesWithoutQuotes = lanesWithoutQuotes(problem);
        List<Candidate> candidates = feasibleCandidates(problem);
        int maxSize = Math.min(problem.maxTransporters(), problem.transporterIds().size());
        if (candidates.isEmpty()) {
            throw new NoFeasibleAssignmentException(maxSize, lanesWithoutQuotes, "brute force: infeasible");
        }
        Candidate best = candidates.stream().min(PREFERENCE).orElseThrow();
        if (best.cost().bitLength() > 63) { // even the cheapest feasible total does not fit in a long
            throw new ArithmeticException("brute force: every feasible total exceeds Long.MAX_VALUE");
        }
        return new AssignmentSolution(best.cost().longValueExact(), best.assignments(), best.members());
    }

    /** Every feasible combination of size 1..K, each evaluated independently. */
    List<Candidate> feasibleCandidates(OptimizationProblem problem) {
        List<Long> transporters = problem.transporterIds().stream().sorted().toList();
        List<Long> lanes = problem.laneIds().stream().sorted().toList();
        Map<Long, Map<Long, Long>> priceByLane = priceByLane(problem);
        int maxSize = Math.min(problem.maxTransporters(), transporters.size());

        List<Candidate> candidates = new ArrayList<>();
        for (int mask = 1; mask < (1 << transporters.size()); mask++) {
            if (Integer.bitCount(mask) > maxSize) {
                continue;
            }
            List<Long> members = new ArrayList<>();
            for (int bit = 0; bit < transporters.size(); bit++) {
                if ((mask & (1 << bit)) != 0) {
                    members.add(transporters.get(bit));
                }
            }
            evaluate(members, lanes, priceByLane).ifPresent(candidates::add);
        }
        return candidates;
    }

    private static Optional<Candidate> evaluate(
            List<Long> members, List<Long> lanes, Map<Long, Map<Long, Long>> priceByLane) {
        BigInteger cost = BigInteger.ZERO;
        List<LaneAssignment> assignments = new ArrayList<>();
        for (long lane : lanes) {
            Long bestTransporter = null;
            long bestQuote = 0;
            for (long member : members) { // members ascending, so strict '<' keeps the lower id on ties
                Long quote = priceByLane.get(lane).get(member);
                if (quote != null && (bestTransporter == null || quote < bestQuote)) {
                    bestTransporter = member;
                    bestQuote = quote;
                }
            }
            if (bestTransporter == null) {
                return Optional.empty();
            }
            cost = cost.add(BigInteger.valueOf(bestQuote));
            assignments.add(new LaneAssignment(lane, bestTransporter, bestQuote));
        }
        return Optional.of(new Candidate(cost, List.copyOf(members), assignments));
    }

    private static Map<Long, Map<Long, Long>> priceByLane(OptimizationProblem problem) {
        Map<Long, Map<Long, Long>> prices = new HashMap<>();
        problem.laneIds().forEach(lane -> prices.put(lane, new HashMap<>()));
        problem.quotes().forEach(q -> prices.get(q.laneId()).put(q.transporterId(), q.amount()));
        return prices;
    }

    private static List<Long> lanesWithoutQuotes(OptimizationProblem problem) {
        Map<Long, Map<Long, Long>> prices = priceByLane(problem);
        return problem.laneIds().stream().sorted().filter(lane -> prices.get(lane).isEmpty()).toList();
    }

    static int compareLexicographically(List<Long> a, List<Long> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int byElement = Long.compare(a.get(i), b.get(i));
            if (byElement != 0) {
                return byElement;
            }
        }
        return Integer.compare(a.size(), b.size());
    }
}
