/*
 * Copyright (C) http://www.programcreek.com/2014/03/leetcode-combinations-java/
 */
package org.vermeir.poker.util;

import java.util.ArrayList;
import java.util.List;

/**
 * @author http://www.programcreek.com/2014/03/leetcode-combinations-java/
 */
public class Combinations {

    public static List<List<Integer>> combine(int n, int k) {
        List<List<Integer>> result = new ArrayList<>();

        if (n <= 0 || n < k) {
            return result;
        }

        List<Integer> item = new ArrayList<>();
        dfs(n, k, 1, item, result); // because it needs to begin from 1

        return result;
    }

    private static void dfs(int n, int k, int start, List<Integer> item, List<List<Integer>> res) {
        if (item.size() == k) {
            res.add(new ArrayList<>(item));
            return;
        }

        for (int i = start; i <= n; i++) {
            item.add(i);
            dfs(n, k, i + 1, item, res);
            item.remove(item.size() - 1);
        }
    }
}
