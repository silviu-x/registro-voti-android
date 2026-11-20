package it.registrovoti.mobile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GradeMath {
    private GradeMath() {}

    public static double average(List<? extends Map<?, ?>> rows, boolean bySubject) {
        if (rows.isEmpty()) return Double.NaN;
        double sum = 0;
        Map<Object, double[]> totals = new HashMap<>();
        for (Map<?, ?> row : rows) {
            double score = ((Number) row.get("value")).doubleValue();
            sum += score;
            if (bySubject) {
                double[] entry = totals.computeIfAbsent(row.get("subject"), k -> new double[2]);
                entry[0] += score;
                entry[1]++;
            }
        }
        if (!bySubject) return sum / rows.size();
        double subjectSum = 0;
        for (double[] entry : totals.values()) subjectSum += entry[0] / entry[1];
        return subjectSum / totals.size();
    }

    public static List<Double> dailyAverages(Collection<? extends List<? extends Map<?, ?>>> days, boolean bySubject) {
        List<Double> points = new ArrayList<>(days.size());
        Map<Object, double[]> totals = new HashMap<>();
        double sum = 0;
        double sumOfMeans = 0;
        int count = 0;
        for (List<? extends Map<?, ?>> day : days) {
            for (Map<?, ?> row : day) {
                double score = ((Number) row.get("value")).doubleValue();
                sum += score;
                count++;
                if (bySubject) {
                    double[] subject = totals.computeIfAbsent(row.get("subject"), k -> new double[2]);
                    if (subject[1] > 0) sumOfMeans -= subject[0] / subject[1];
                    subject[0] += score;
                    subject[1]++;
                    sumOfMeans += subject[0] / subject[1];
                }
            }
            points.add(bySubject ? sumOfMeans / totals.size() : sum / count);
        }
        return points;
    }

    public static double needed(List<? extends Map<?, ?>> rows, String subject, double target,
                                int count, boolean general, boolean bySubject) {
        if (count < 1) throw new IllegalArgumentException("Numero voti non valido");
        int existing = 0;
        double sum = 0;
        Map<Object, double[]> others = new HashMap<>();
        for (Map<?, ?> row : rows) {
            double score = ((Number) row.get("value")).doubleValue();
            if (subject.equals(row.get("subject")) || (general && !bySubject)) {
                sum += score;
                existing++;
            } else if (general) {
                double[] entry = others.computeIfAbsent(row.get("subject"), k -> new double[2]);
                entry[0] += score;
                entry[1]++;
            }
        }
        double required = target * (others.size() + 1);
        for (double[] entry : others.values()) required -= entry[0] / entry[1];
        return (required * (existing + count) - sum) / count;
    }
}
