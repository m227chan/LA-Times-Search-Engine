/*
 * Date: November 21, 2023
 * Name: Matthew Chan
 * 
 * Program Description:
 * The Java program, named BM25, serves as an implementation for the BM25 ranking algorithm, 
 * a popular information retrieval method. The program takes four command-line arguments: 
 * the path to a compressed data file, the path to a queries file, the path to store the results, 
 * and a flag (0 or 1) indicating whether to apply stemming to the index. It utilizes tokenization, 
 * reads serialized objects representing the index and lexicon, and computes BM25 scores for each 
 * document based on the provided queries. The results, containing topic IDs, document ranks, 
 * scores, and other relevant information, are then written to an output file. The code is 
 * structured with error-checking for file paths and arguments, making it a comprehensive tool 
 * for BM25-based ranking evaluation in information retrieval scenarios.
 * 
 * Command Line Argument Inputs:
 * javac BM25.java
 * java BM25.java latimes_index queries.txt bm25-baseline-m227chan.txt 0
 * java BM25.java latimes_index queries.txt bm25-stem-m227chan.txt 1
 * 
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

@SuppressWarnings("unchecked")
public class BM25 {

    // Check if a character is alphanumeric
    private static boolean isAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    // Tokenize a given text based on Strohman's pseudocode
    private static List<String> tokenize(String text, String stem) {
        List<String> tokens = new ArrayList<String>();
        text = text.toLowerCase();
        int start = 0;
        int i = 0;

        while (i < text.length()) {
            char c = text.charAt(i);

            if (!isAlphanumeric(c)) {
                if (start != i) {
                    String token = text.substring(start, i);
                    if (stem.equals("1")) {
                        token = PorterStemmer.stem(token);
                    }
                    tokens.add(token);
                }
                start = i + 1;
            }
            i++;
        }

        if (start != i) {
            String token = text.substring(start, i);
            if (stem.equals("1")) {
                token = PorterStemmer.stem(token);
            }
            tokens.add(token);
        }
        return tokens;
    }

    public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException {
        // Check if the correct number of command line arguments is provided
        if (args.length != 4) {
            throw new IllegalArgumentException("Please include a valid path to gzip'd data file, a path to a query txt file, a path to store the results, and specify the third argument as 0 for no stem or 1 for stem.");
        }

        // Check if the data file path exists
        String dataPath = args[0];
        File dataFile = new File(dataPath);
        if (!dataFile.exists()) {
            throw new FileNotFoundException(dataPath + " path does not exist");
        }

        // Check if the queries file path exists
        String queryPath = args[1];
        File queryFile = new File(queryPath);
        if (!queryFile.exists()) {
            throw new FileNotFoundException(queryPath + " path does not exist");
        }

        // Check if the results file path already exists
        File resultFile = new File(args[2]);
        if (!resultFile.createNewFile()) {
            throw new FileAlreadyExistsException("Result file path already exists");
        }

        // Validate the stem argument
        String stem = args[3];
        if ((!stem.equals("0") && !stem.equals("1"))) {
            throw new IllegalArgumentException("The third argument must be either: 0 (no index stemming) or 1 (with index stemming)");
        }

        // Check if the indexMap file path exists
        File IMFile = new File(dataPath + "\\indexMap.txt");
        if (!IMFile.exists()) {
            throw new FileNotFoundException("indexMap.txt path does not exist");
        }

        // Read indexMap file into a list
        ArrayList<String> IMList = new ArrayList<>();
        String currLineIM = "";
        Scanner myReaderIM = new Scanner(IMFile);
        while (myReaderIM.hasNextLine()) {
            currLineIM = myReaderIM.nextLine();
            IMList.add(currLineIM);
        }
        myReaderIM.close();

        // Check if the doc-length file path exists in dataPath
        File docLengthsFile = new File(dataPath + "\\doc-lengths.txt");
        if (!docLengthsFile.exists()) {
            throw new FileNotFoundException("doc-lengths.txt path does not exist");
        }

        // Read doc-length file into a list and calculate average document length
        ArrayList<Integer> docLengthsList = new ArrayList<>();
        int currLength = 0;
        Double sum = 0.0;
        Double N = 0.0;
        Double avgDocLength = -1.0;
        Scanner myReader = new Scanner(docLengthsFile);
        while (myReader.hasNextLine()) {
            currLength = Integer.parseInt(myReader.nextLine());
            docLengthsList.add(currLength);
            sum = sum + currLength;
            N++;
        }
        myReader.close();
        if (N > 0) {
            avgDocLength = sum / N;
        }

        // Read queries from the query file
        BufferedReader brQueries = new BufferedReader(new FileReader(queryFile));
        StringBuilder sbResult = new StringBuilder();

        // Read lexiconTermToID and invIndex objects from serialized files
        FileInputStream fisLexiconTermToID = new FileInputStream(dataPath + "\\lexiconTermToID.txt");
        ObjectInputStream oisLexiconTermToID = new ObjectInputStream(fisLexiconTermToID);
        HashMap<String, Integer> lexiconTermToID = (HashMap<String, Integer>) oisLexiconTermToID.readObject();

        FileInputStream fisInvIndex = new FileInputStream(dataPath + "\\invIndex.txt");
        ObjectInputStream oisInvIndex = new ObjectInputStream(fisInvIndex);
        HashMap<Integer, List<Integer>> invIndex = (HashMap<Integer, List<Integer>>) oisInvIndex.readObject();

        String currLine = "";
        String query = "";
        int termID = -1;
        int topicID = 0;
        String docno = "NA";
        Double score = 0.0;
        int rank = 0;
        String runTag = "BM25";
        String Q = "Q0";
        Double b = 0.75;
        Double k1 = 1.2;

        // Iterate through each query in the file
        while ((currLine = brQueries.readLine()) != null) {
            topicID = Integer.valueOf(currLine);

            if ((currLine = brQueries.readLine()) != null) {
                query = currLine;
            }

            // Tokenize the query
            List<String> tokens = new ArrayList<>();
            tokens = tokenize(query, stem);

            // Hashtable to store document scores
            Hashtable<Integer, Double> accumulator = new Hashtable<>(); // doc id -> score

            if (!tokens.isEmpty()) {
                // Iterate through each term in the query
                for (int i = 0; i < tokens.size(); i++) {
                    if (lexiconTermToID.containsKey(tokens.get(i))) {
                        termID = lexiconTermToID.get(tokens.get(i));
                    } else {
                        continue;
                    }
                    // Get postings list for the term
                    List<Integer> postings = invIndex.get(termID);

                    Double scoreBM25 = 0.0;
                    Double n = (double) postings.size() / 2;

                    // Calculate idf
                    Double idf = Math.log((N - n + 0.5) / (n + 0.5));

                    // Compute BM25 for each document
                    for (int j = 0; j < postings.size(); j = j + 2) {
                        int docid = postings.get(j);
                        Double termFreq = (double) postings.get(j + 1);
                        Double docLength = (double) docLengthsList.get(docid);

                        // Calculate length normalization (K)
                        Double k = k1 * ((1 - b) + b * docLength / avgDocLength);

                        // Calculate tf weight
                        Double tf = termFreq / (k + termFreq);

                        scoreBM25 = tf * idf;

                        // Update accumulator
                        if (!accumulator.containsKey(docid)) {
                            accumulator.put(docid, scoreBM25);
                        } else {
                            accumulator.put(docid, accumulator.get(docid) + scoreBM25);
                        }
                    }
                }
            }

            // Sort accumulator in descending order
            ArrayList<Map.Entry<Integer, Double>> accumulatorEntries = new ArrayList<>(accumulator.entrySet());
            Collections.sort(accumulatorEntries, new Comparator<Map.Entry<Integer, Double>>() {
                public int compare(Map.Entry<Integer, Double> a, Map.Entry<Integer, Double> b) {
                    return Double.compare(b.getValue(), a.getValue());
                }
            });

            // Write the top-ranked results to the StringBuilder
            for (int i = 0; i < accumulatorEntries.size(); i++) {
                if (i >= 1000) {
                    break;
                }
                Map.Entry<Integer, Double> entry = accumulatorEntries.get(i);
                score = entry.getValue();
                docno = IMList.get(entry.getKey());
                rank = i + 1;
                sbResult.append(topicID + " " + Q + " " + docno + " " + rank + " " + score + " " + runTag + "\n");
            }
        }

        // Remove the last empty line
        if (sbResult.length() != 0) {
            sbResult.setLength(sbResult.length() - 1);
        }

        // Write the results to the output file
        FileWriter writerResult = new FileWriter(resultFile);
        writerResult.write(sbResult.toString());
        writerResult.close();

        // Close object input streams
        oisLexiconTermToID.close();
        oisInvIndex.close();

        // Close buffered reader for queries
        brQueries.close();
    }
}
