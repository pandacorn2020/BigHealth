package com.bighealth.util;
public class CosineSimilarity {

    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must be of the same length");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static void main(String[] args) {
        float[] vectorA = {1.0f, 2.0f, 3.0f};
        float[] vectorB = {4.0f, 5.0f, 6.0f};

        double similarity = cosineSimilarity(vectorA, vectorB);
        System.out.println("Cosine Similarity: " + similarity);
    }
}