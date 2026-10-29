package com.routeoptimizer.research.reward.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Calculates the Edit Distance (Levenshtein distance) between a predicted task sequence and a reference task sequence.
 */
@Service
public class EditDistanceCalculator {

    /**
     * Calculates the minimum edit operations (insertions, deletions, substitutions) needed 
     * to transform the predicted sequence into the reference sequence.
     */
    public <T> int calculateEditDistance(List<T> predicted, List<?> reference) {
        if (predicted == null || reference == null) {
            throw new IllegalArgumentException("Sequences cannot be null");
        }

        int n = predicted.size();
        int m = reference.size();

        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[][] dp = new int[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (predicted.get(i - 1).equals(reference.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    int substitution = dp[i - 1][j - 1] + 1;
                    int insertion = dp[i][j - 1] + 1;
                    int deletion = dp[i - 1][j] + 1;
                    
                    dp[i][j] = Math.min(substitution, Math.min(insertion, deletion));
                }
            }
        }

        return dp[n][m];
    }
}
