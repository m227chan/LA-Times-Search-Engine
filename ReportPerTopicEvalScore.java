/*
 * Date: November 7, 2023
 * Name: Matthew Chan
 * 
 * Program Description:
 * The ReportPerTopicEvalScore Java program serves as an evaluator for information 
 * retrieval systems. It takes two command line arguments— the path to a search engine's 
 * results file and a file containing relevance judgments (qrels). The program calculates 
 * three key evaluation metrics for each query: Average Precision (AP), Precision at Rank 
 * 10 (P_10), and Normalized Discounted Cumulative Gain (NDCG). The results are then 
 * written to an output file named "output.txt." The code is structured with modular 
 * methods for each metric, enhancing readability and maintainability. The program enforces 
 * correct usage and file existence, providing a comprehensive tool for assessing the 
 * performance of search engines based on relevance judgments.
 * 
 * Command Line Argument Inputs:
 * javac ReportPerTopicEvalScore.java
 * java ReportPerTopicEvalScore.java results-files.results qrels\file.txt
 * 
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class ReportPerTopicEvalScore {

    // Calculates Average Precision for a given query ID
    private static float calculateAveragePrecision(String queryID, 
                                                   Results results, 
                                                   RelevanceJudgements relevanceJudgments) 
                                                   throws Exception {
        float rel = relevanceJudgments.NumRelevant(queryID);
        ArrayList<Results.Result> resultsList = results.QueryResults(queryID);
        if (resultsList == null) { return 0; }
        float n = 1;
        float numRelevant = 0;
        float precisionAtRankN = 0;
        float sum = 0;
        for (Results.Result result : resultsList) {
            // Check if the result is relevant
            if (relevanceJudgments.IsRelevant(queryID, result.docID())) {
                if (numRelevant == 1000) {
                    precisionAtRankN = 0;
                }
                numRelevant++;
                precisionAtRankN = numRelevant / n;
                sum = sum + precisionAtRankN;
            }

            // Break loop when reaching the maximum rank (1000)
            if (n == 1000) {
                break;
            }
            n++;
        }
        float averagePrecision = sum / rel;
        return averagePrecision;
    }

    // Calculates Precision at rank 10 for a given query ID
    private static float calculatePrecision(String queryID, 
                                            Results results, 
                                            RelevanceJudgements relevanceJudgments) 
                                            throws Exception {
        ArrayList<Results.Result> resultsList = results.QueryResults(queryID);
        if (resultsList == null) { return 0; }
        float n = 0;
        float numRelevant = 0;
        for (Results.Result result : resultsList) {
            n++;
            if (relevanceJudgments.IsRelevant(queryID, result.docID())) {
                numRelevant++;
            }
            // Break loop when reaching rank 10
            if (n == 10) {
                break;
            }
        }
        float precision = numRelevant / n;
        return precision;
    }

    // Calculates Normalized Discounted Cumulative Gain (NDCG) for a given query ID
    private static float calculateNDCG(String queryID, 
                                       Results results, 
                                       RelevanceJudgements relevanceJudgments, 
                                       int maxRank) 
                                       throws Exception {
        float dcg = 0;
        float i = 1;
        ArrayList<Results.Result> resultsList = results.QueryResults(queryID);
        if (resultsList == null) { return 0; }
        // Calculate DCG
        for (Results.Result result : resultsList) {
            if (relevanceJudgments.IsRelevant(queryID, result.docID())) {
                dcg = dcg + 1 / log2(i + 1);
            }
            i++;
            // Break loop when reaching the maximum rank
            if (i > maxRank) {
                break;
            }
        }

        // Calculate IDCG
        float idcg = 0;
        int numRelevant = relevanceJudgments.NumRelevant(queryID);
        if (numRelevant > maxRank) {
            numRelevant = maxRank;
        }
        for (int j = 1; j <= numRelevant; j++) {
            idcg = idcg + 1 / log2(j + 1);
        }

        // Calculate NDCG
        float ndcg = dcg / idcg;
        return ndcg;
    }

    // Logarithm to the base 2
    public static float log2(float N)
    {
        float result = (float)(Math.log(N) / Math.log(2));
        return result;
    }

    // Main method to run the evaluation
    public static void main(String[] args) throws FileNotFoundException, IOException, Exception {
        // Check if the correct number of command line arguments is provided
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "Please include valid path to a results file and a path to the qrels file"
            );
        }

        // Read the paths from command line arguments
        String resultsPath = args[0];
        File resultsFile = new File(resultsPath);
        if (!resultsFile.exists()) {
            throw new FileNotFoundException(resultsPath + " path does not exist");
        }

        String qrelsPath = args[1];
        File qrelsFile = new File(qrelsPath);
        if (!qrelsFile.exists()) {
            throw new FileNotFoundException(qrelsPath + " path does not exist");
        }

        // Read in relevance judgments (qrels)
        QRels qrels = new QRels(qrelsPath);
        RelevanceJudgements relevanceJudgments = qrels.judgments;

        // Read in results file
        ResultsFile rf = new ResultsFile(resultsPath);
        Results results = rf.results;

        // Get list of query IDs
        ArrayList<String> queryIDs = relevanceJudgments.QueryIDs();

        // Create a writer for the output file
        PrintWriter writer = new PrintWriter(new FileWriter("output.txt"));

        // Calculate and write Average Precision for each query
        for (String queryID : queryIDs) {
            writer.printf("ap %s %.4f\n", queryID,
                          calculateAveragePrecision(queryID, results, relevanceJudgments));
        }

        // Calculate and write NDCG at rank 10 for each query
        for (String queryID : queryIDs) {
            writer.printf("ndcg_cut_10 %s %.4f\n", queryID,
                          calculateNDCG(queryID, results, relevanceJudgments, 10));
        }

        // Calculate and write NDCG at rank 1000 for each query
        for (String queryID : queryIDs) {
            writer.printf("ndcg_cut_1000 %s %.4f\n", queryID,
                          calculateNDCG(queryID, results, relevanceJudgments, 1000));
        }

        // Calculate and write Precision at rank 10 for each query
        for (String queryID : queryIDs) {
            writer.printf("P_10 %s %.4f\n", queryID,
                          calculatePrecision(queryID, results, relevanceJudgments));
        }

        // Close the writer
        writer.close();
    }
}
