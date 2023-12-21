/*
 * Date: December 5, 2023
 * Name: Matthew Chan
 * 
 * Program Description:
 * The InteractiveBM25 Java Program, is an information retrieval system that utilizes the 
 * BM25 ranking algorithm to score and retrieve documents based on user-inputted queries. 
 * The program takes as input a path to a Gzip'd data file containing indexed documents 
 * and associated metadata. It employs tokenization, regular expressions, and various 
 * calculations to process the user's query, ranking the matching documents according to 
 * BM25 scores. The program then presents the top retrieval results, displaying relevant 
 * metadata such as headlines, dates, and document snippets. Users can interactively choose 
 * to view the full content of a specific document or enter new queries. The implementation 
 * includes file reading, data manipulation, and user interface components to facilitate a 
 * dynamic and informative search experience.
 * 
 * Command Line Argument Inputs:
 * javac InteractiveBM25.java
 * java InteractiveBM25.java latimes_index
 * 
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.text.BreakIterator;

@SuppressWarnings("unchecked")
public class InteractiveBM25 {

    // Check if a character is alphanumeric
    private static boolean isAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    // Tokenize code using pseudocode by Strohman, referenced in Professor Mark Smucker's lecture on Sept. 22, 2023
    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<String>();

        text = text.toLowerCase();

        int start = 0;
        int i = 0;

        while (i < text.length()) {
            char c = text.charAt(i);

            if (!isAlphanumeric(c)) {
                if (start != i) {
                    String token = text.substring(start, i);
                    tokens.add(token);
                }
                start = i + 1;
            }
            i++;
        }

        if (start != i) {
            String token = text.substring(start, i);
            tokens.add(token);
        }

        return tokens;
    }

    // Read a Gzip file and return a BufferedReader
    private static BufferedReader readGzip(String path) throws IOException {
        // Open Gzip file
        InputStream fileStream = new FileInputStream(path);
        InputStream gzipStream = new GZIPInputStream(fileStream);
        Reader decoder = new InputStreamReader(gzipStream);
        BufferedReader buffered = new BufferedReader(decoder);

        return buffered;
    }

    // Extract text from a given string using a regex pattern
    private static String extractText(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1)
                    .replaceAll("\n", "")
                    .replaceAll("<P>", "");
        } else {
            return "";
        }
    }

    // Calculate a score based on sentence number, sentence, and query tokens
    private static Integer calculateScore(int sentenceNum, String sentence, List<String> queryTokens) {
        int l = 0; // First or second sentence
        int c = 0; // Query term occurrence in sentence
        int d = 0; // Distinct query term occurrence in sentence

        if (sentenceNum == 1) {
            l = 2;
        } else if (sentenceNum == 2) {
            l = 1;
        }

        List<String> sentenceTokens = tokenize(sentence);
        List<String> distinctTerms = new ArrayList<>();

        for (int i = 0; i < sentenceTokens.size(); i++) {
            String term = sentenceTokens.get(i);
            if (queryTokens.contains(term)) {
                c++;
            }
            if (!distinctTerms.contains(term) && queryTokens.contains(term)) {
                distinctTerms.add(term);
            }
        }

        d = distinctTerms.size();

        return l + c + d;
    }

    // Display retrieval results for a given rank
    private static void showRetrieval(ArrayList<Entry<Integer, Double>> accumulatorEntries,
                                      ArrayList<String> IMList,
                                      int rank,
                                      String dataPath,
                                      List<String> tokens) throws IOException {

        Map.Entry<Integer, Double> entry = accumulatorEntries.get(rank - 1);
        String docno = IMList.get(entry.getKey());

        // Get year, month, day
        String year = docno.substring(6, 8);
        String month = docno.substring(2, 4);
        String day = docno.substring(4, 6);

        String datePath = dataPath + "/" + year + "/" + month + "/" + day + "/" + docno + ".gzip";
        File docFile = new File(datePath);

        // Use regex to find text between tags
        final Pattern headlinePattern = Pattern.compile("<HEADLINE>(.+?)</HEADLINE>", Pattern.DOTALL);
        final Pattern datePattern = Pattern.compile("<DATE>(.+?)</DATE>", Pattern.DOTALL);
        final Pattern textPattern = Pattern.compile("<TEXT>(.+?)</TEXT>", Pattern.DOTALL);
        final Pattern graphicPattern = Pattern.compile("<GRAPHIC>(.+?)</GRAPHIC>", Pattern.DOTALL);

        String headline = "";
        String date = "";
        String snippet = "";

        // Extract snippet, headline
        if (docFile.exists()) {
            // Print raw document contents
            BufferedReader buffered = readGzip(datePath);
            StringBuilder currDoc = new StringBuilder();
            String curr;
            while ((curr = buffered.readLine()) != null) {
                currDoc.append(curr);
            }

            HashMap<String, Integer> scoreAggregator = new HashMap<>();

            headline = extractText(currDoc.toString(), headlinePattern).replace("</P>", "");

            if (headline.length() > 50) {
                headline = headline.substring(0, 50) + "...";
            }

            String source = (extractText(currDoc.toString(), textPattern) +
                    extractText(currDoc.toString(), graphicPattern))
                    .replaceAll("<[^>]*>", "")
                    .replaceAll("\\s+", " ");

            // Break text into sentences
            BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
            iterator.setText(source);
            int start = iterator.first();
            int sentenceNum = 1;
            // Iterate through each sentence
            for (int end = iterator.next();
                 end != BreakIterator.DONE;
                 start = end, end = iterator.next()) {
                String sentence = source.substring(start, end);
                scoreAggregator.put(sentence, calculateScore(sentenceNum, sentence, tokens));
                if (sentenceNum <= 2) {
                    sentenceNum++;
                }
            }

            // Sort accumulator descending
            ArrayList<Map.Entry<String, Integer>> scoreAggregatorEntries = new ArrayList<>(scoreAggregator.entrySet());
            Collections.sort(
                    scoreAggregatorEntries,
                    new Comparator<Map.Entry<String, Integer>>() {
                        public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                            return Integer.compare(b.getValue(), a.getValue());
                        }
                    }
            );

            if (scoreAggregatorEntries.size() == 1) {
                snippet = scoreAggregatorEntries.get(0).getKey();
            } else if (scoreAggregatorEntries.size() >= 2) {
                snippet = scoreAggregatorEntries.get(0).getKey() + scoreAggregatorEntries.get(1).getKey();
            }

            String[] dateSplit = extractText(currDoc.toString(), datePattern).replaceAll("</P>", "").split(", ");
            if (dateSplit.length > 2) {
                date = dateSplit[0] + ", " + dateSplit[1];
            }

            buffered.close();
        } else {
            throw new FileNotFoundException("File not found");
        }

        if (headline.equals("") && snippet.length() > 0) {
            if (snippet.length() >= 50) {
                headline = snippet.substring(0, 50) + "...";
            } else {
                headline = snippet.substring(0, snippet.length()) + "...";
            }
        }

        System.out.printf("%d. %s (%s)%n%s (%s)%n", rank, headline, date, snippet, docno);
    }

    // Output the content of a document given its metadata store path root and docno
    public static void outputDoc(String metaDataStorePathRoot, String docno) throws FileNotFoundException, IOException {

        String year = docno.substring(6, 8);
        String month = docno.substring(2, 4);
        String day = docno.substring(4, 6);

        String datePath = metaDataStorePathRoot + "/" + year + "/" + month + "/" + day + "/" + docno + ".gzip";
        File docFile = new File(datePath);

        if (docFile.exists()) {
            // Print raw document contents
            BufferedReader buffered = readGzip(datePath);
            String currLine;
            while ((currLine = buffered.readLine()) != null) {
                System.out.println(currLine);
            }
            buffered.close();

        } else {
            throw new FileNotFoundException("File not found");
        }
    }

    public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException {

        // Throw an error if 1 argument is not given and give a help message
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Please include a valid path to a gzip'd data file. For example, " +
                            "'java BM25.java latimes_index'"
            );
        }

        // Throw an error if the data file path does not exist
        String dataPath = args[0];
        File dataFile = new File(dataPath);
        if (!dataFile.exists()) {
            throw new FileNotFoundException(dataPath + " path does not exist");
        }

        // Throw an error if the indexMap file path does not exist
        File IMFile = new File(dataPath + "\\indexMap.txt");
        if (!IMFile.exists()) {
            throw new FileNotFoundException("indexMap.txt path does not exist");
        }

        ArrayList<String> IMList = new ArrayList<>();
        String currLineIM = "";
        Scanner myReaderIM = new Scanner(IMFile);
        while (myReaderIM.hasNextLine()) {
            currLineIM = myReaderIM.nextLine();
            IMList.add(currLineIM);
        }
        myReaderIM.close();

        // Throw an error if the doc-length file path does not exist in dataPath
        File docLengthsFile = new File(dataPath + "\\doc-lengths.txt");
        if (!docLengthsFile.exists()) {
            throw new FileNotFoundException("doc-lengths.txt path does not exist");
        }

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

        // Reference: https://docs.oracle.com/javase/8/docs/api/java/io/ObjectInputStream.html
        FileInputStream fisLexiconTermToID = new FileInputStream(dataPath + "\\lexiconTermToID.txt");
        ObjectInputStream oisLexiconTermToID = new ObjectInputStream(fisLexiconTermToID);
        HashMap<String, Integer> lexiconTermToID = (HashMap<String, Integer>) oisLexiconTermToID.readObject();
        oisLexiconTermToID.close();

        BufferedReader bufferedIM = new BufferedReader(new FileReader(new File(dataPath + "\\indexMap.txt")));
        ArrayList<String> indexMap = new ArrayList<>();
        while ((currLineIM = bufferedIM.readLine()) != null) {
            indexMap.add(currLineIM);
        }

        FileInputStream fisInvIndex = new FileInputStream(dataPath + "\\invIndex.txt");
        ObjectInputStream oisInvIndex = new ObjectInputStream(fisInvIndex);
        HashMap<Integer, List<Integer>> invIndex = (HashMap<Integer, List<Integer>>) oisInvIndex.readObject();
        oisInvIndex.close();

        // Using Scanner for Getting Input from User
        Scanner in = new Scanner(System.in);

        main:
        while (true) {

            System.out.print("Please enter a query here: ");
            String query = in.nextLine();

            long startTime = System.currentTimeMillis();

            int termID = -1;
            Double b = 0.75;
            Double k1 = 1.2;

            List<String> tokens = new ArrayList<>();
            tokens = tokenize(query);

            HashMap<Integer, Double> accumulator = new HashMap<>(); // doc id -> score

            if (!tokens.isEmpty()) {
                // Iterate through each term in the query
                for (int i = 0; i < tokens.size(); i++) {
                    if (lexiconTermToID.containsKey(tokens.get(i))) {
                        termID = lexiconTermToID.get(tokens.get(i));
                    } else {
                        continue;
                    }
                    List<Integer> postings = invIndex.get(termID); // [doc id, count of term i, doc id, count of term i, ...]
                    Double scoreBM25 = 0.0;
                    Double n = (double) postings.size() / 2;
                    // calculate idf
                    Double idf = Math.log((N - n + 0.5) / (n + 0.5));

                    // compute BM25
                    for (int j = 0; j < postings.size(); j=j+2) {

                        int docid = postings.get(j);
                        Double term_freq = (double) postings.get(j+1);
                        Double docLength = (double) docLengthsList.get(docid);

                        // calculate length normalization K
                        Double k = k1 * ((1 - b) + b * docLength / avgDocLength);

                        // calculate tf weight
                        Double tf = term_freq / (k + term_freq);

                        scoreBM25 = tf * idf;

                        if (!accumulator.containsKey(docid)) {
                            accumulator.put(docid, scoreBM25);
                        } else {
                            accumulator.put(docid, accumulator.get(docid) + scoreBM25);
                        }
                    }
                }
            } else {
                System.out.println("Error: please input a query");
            }

            // sort accumulator descending
            ArrayList<Map.Entry<Integer, Double>> accumulatorEntries = new ArrayList<>(accumulator.entrySet());
            Collections.sort(
                accumulatorEntries,   
                new Comparator<Map.Entry<Integer,Double>>() {
                    public int compare(Map.Entry<Integer,Double> a, Map.Entry<Integer,Double> b) {
                        return Double.compare(b.getValue(), a.getValue());
                    }
                }
            );

            for (int i = 0; i < accumulatorEntries.size(); i++) {
                if (i >= 10) {
                    break;
                }
                showRetrieval(accumulatorEntries, IMList, i+1, dataPath, tokens);
                long endTime   = System.currentTimeMillis();
                float totalTime = endTime - startTime;
                System.out.printf("Retrieval took %.4f seconds %n%n", totalTime / 1000);
            }
            
            while(true) {
                // ask user if they want to enter another query. If no, end the program.
                System.out.print("Enter N for new query, Q for quit, or a numeric rank: ");
                String response = in.nextLine();
                if (response.toLowerCase().equals("q")) {
                    System.out.println("Ending program. Goodbye.");
                    break main;
                } else if (response.toLowerCase().equals("n")) {
                    continue main;
                } else if (response.matches("\\d+")) {
                    int rank = Integer.parseInt(response);
                    if (rank <= accumulatorEntries.size() && rank > 0) {
                        int docid = accumulatorEntries.get(Integer.parseInt(response)-1).getKey();
                        String docno = indexMap.get(docid);
                        System.out.println(docno);
                        outputDoc(dataPath, docno);
                    } else {
                        System.out.println("Inputted rank exceeds the number of documents retrieved");
                    }
                    continue;
                } else {
                    System.out.println("invalid input");
                }
            }
        }
        in.close();
    }
}