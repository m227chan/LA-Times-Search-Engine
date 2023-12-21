/*
 * Date: November 21, 2023
 * Name: Matthew Chan
 * 
 * Program Description:
 * This Index Engine Java Program serves as an information retrieval system for processing 
 * and indexing a collection of documents. It takes as input a gzip-compressed data file 
 * containing documents with specific tags such as <DOCNO>, <HEADLINE>, <TEXT>, and <GRAPHIC>. 
 * The program extracts relevant information from these tags, including document numbers, 
 * dates, headlines, and textual content. It tokenizes and processes the text, performing optional 
 * stemming, and then constructs an inverted index to map terms to document IDs and their corresponding 
 * word frequencies. The program generates metadata for each document, compresses the documents, 
 * and stores them in a structured directory based on their publication dates. Additionally, it creates 
 * lexicon files and an inverted index file to facilitate efficient retrieval of information. The user 
 * can specify whether or not to apply stemming during the tokenization process.
 * 
 * NOTE: first argument is for data directory, second is for path to store the metadata,
 * third argument specifies whether to stem or not (0 for no stemming or 1 for stemming)
 * 
 * Command Line Argument Inputs:
 * javac IndexEngine.java
 * java IndexEngine.java data\latimes.gz latimes_index 0
 * java IndexEngine.java data\latimes.gz latimes_index 1
 * 
 */

 import java.io.Reader;
 import java.io.BufferedReader;
 import java.io.BufferedWriter;
 import java.io.InputStreamReader;
 import java.io.ObjectOutputStream;
 import java.io.OutputStreamWriter;
 import java.io.FileInputStream;
 import java.io.InputStream;
 import java.util.ArrayList;
 import java.util.HashMap;
 import java.util.List;
 import java.util.regex.Matcher;
 import java.util.regex.Pattern;
 import java.util.zip.GZIPInputStream;
 import java.util.zip.GZIPOutputStream;
 import java.io.File;
 import java.io.IOException;
 import java.io.FileNotFoundException;
 import java.io.FileOutputStream;
 import java.io.FileWriter;
 import java.nio.file.FileAlreadyExistsException;
 import java.time.LocalDate;
 import java.time.format.DateTimeFormatter;

public class IndexEngine {

    // Extracts and returns the document number from a line
    private static String getDocno(String line) throws IOException {
        String output = line.replace("<DOCNO> ", "").replace(" </DOCNO>", "");
        return output;
    }

    // Extracts year, month, and day from the document number
    private static String[] getDate(String docno) throws IOException {
        String year = docno.substring(6, 8);
        String month = docno.substring(2, 4);
        String day = docno.substring(4, 6);

        String[] output = new String[3];
        output[0] = year;
        output[1] = month;
        output[2] = day;

        return output;
    }

    // Converts year, month, and day to a formatted date string
    private static String getWordDate(Integer year, Integer month, Integer day) throws IOException {
        LocalDate rawDate = LocalDate.of(year + 1900, month, day);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy");
        String formattedDate = rawDate.format(formatter);

        return formattedDate;
    }

    // Generates a file path based on the document number, year, month, and day
    private static File getDocFilePathFromDocno(String docno,
                                                String year,
                                                String month,
                                                String day,
                                                String metaDataStorePathRoot) throws IOException {
        String datePath = metaDataStorePathRoot + "/" + year + "/" + month + "/" + day;
        new File(datePath).mkdirs();
        File docFile = new File(datePath + "/" + docno + ".gzip");

        return docFile;
    }

    // Checks if a character is alphanumeric
    private static boolean isAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    // Tokenizes text and applies stemming if specified
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
                        // Apply stemming using PorterStemmer
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
                // Apply stemming using PorterStemmer
                token = PorterStemmer.stem(token);
            }
            tokens.add(token);
        }

        return tokens;
    }

    // Converts tokens to term IDs using lexicon
    private static List<Integer> tokensToIDs(List<String> tokens, HashMap<String, Integer> lexiconTermToID, HashMap<Integer, String> lexiconIDToTerm) {
        List<Integer> tokenIDs = new ArrayList<Integer>();

        for (String token : tokens) {
            if (lexiconTermToID.containsKey(token)) {
                tokenIDs.add(lexiconTermToID.get(token));
            } else {
                int termID = lexiconTermToID.size();
                lexiconTermToID.put(token, termID);
                lexiconIDToTerm.put(termID, token);
                tokenIDs.add(termID);
            }
        }

        return tokenIDs;
    }

    // Counts word occurrences and saves document length
    private static HashMap<Integer, Integer> countWords(List<Integer> tokenIDs, FileWriter writerDocLength) throws IOException {
        HashMap<Integer, Integer> wordCounts = new HashMap<Integer, Integer>();
        int tokenCount = 0;

        for (int i = 0; i < tokenIDs.size(); i++) {
            tokenCount++;
            int termID = tokenIDs.get(i);
            if (wordCounts.containsKey(termID)) {
                wordCounts.put(termID, wordCounts.get(termID) + 1);
            } else {
                wordCounts.put(termID, 1);
            }
        }

        writerDocLength.append(tokenCount + "\n");
        return wordCounts;
    }

    // Updates inverted index with document information
    private static void addToPostings(HashMap<Integer, Integer> wordCounts,
                                      Integer docID,
                                      HashMap<Integer, List<Integer>> invIndex) {
        for (Integer termID : wordCounts.keySet()) {
            int count = wordCounts.get(termID);
            List<Integer> postings = invIndex.getOrDefault(termID, new ArrayList<Integer>());
            postings.add(docID);
            postings.add(count);
            invIndex.put(termID, postings);
        }
    }

    // Extracts text between specified tags using regular expressions
    private static String extractText(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1)
                    .replace("\n", "")
                    .replace("<P>", "")
                    .replace("</P>", "");
        } else {
            return "";
        }
    }

    // Indexes a file and updates lexicon, inverted index, and document metadata
    private static void indexFile(BufferedReader buffered,
                                  String metaDataStorePathRoot,
                                  FileWriter writerIndexMap,
                                  FileWriter writerDocLength,
                                  String stem) throws IOException {

        // Variables to store document information
        String docno = "";
        String date = "";
        String headline = "";
        String text = "";
        String graphic = "";

        // Read the first line and initialize internal ID count
        String currLine;
        Integer internalIdCount = 0;

        // Data structures to store lexicon and inverted index
        HashMap<String, Integer> lexiconTermToID = new HashMap<String, Integer>();
        HashMap<Integer, String> lexiconIDToTerm = new HashMap<Integer, String>();
        HashMap<Integer, List<Integer>> invIndex = new HashMap<Integer, List<Integer>>();

        // StringBuilder to accumulate the current document content
        StringBuilder currDoc = new StringBuilder();

        // Read and process each line in the file
        currDoc.append(buffered.readLine() + "\n"); // append the first line which is <DOC>
        currLine = buffered.readLine();
        currDoc.append(currLine + "\n"); // append the current line to currDoc <DOCNO>

        // Extract docno from line and write to indexMap file
        docno = getDocno(currLine);
        writerIndexMap.append(docno + "\n");

        // Extract date information from docno
        String[] dateArray = getDate(docno);
        date = getWordDate(Integer.parseInt(dateArray[0]), Integer.parseInt(dateArray[1]), Integer.parseInt(dateArray[2]));

        // Generate file path for the current document
        File currDocFile = getDocFilePathFromDocno(docno, dateArray[0], dateArray[1], dateArray[2], metaDataStorePathRoot);

        // Define regex patterns for extracting text between tags
        final Pattern headlinePattern = Pattern.compile("<HEADLINE>(.+?)</HEADLINE>", Pattern.DOTALL);
        final Pattern textPattern = Pattern.compile("<TEXT>(.+?)</TEXT>", Pattern.DOTALL);
        final Pattern graphicPattern = Pattern.compile("<GRAPHIC>(.+?)</GRAPHIC>", Pattern.DOTALL);

        // Process each line until the end of the document
        while ((currLine = buffered.readLine()) != null) {
            if (currLine.contains("</DOC>")) {
                currDoc.append(currLine); // </DOC>

                // Extract text from document using regex patterns
                headline = extractText(currDoc.toString(), headlinePattern);
                text = extractText(currDoc.toString(), textPattern);
                graphic = extractText(currDoc.toString(), graphicPattern);

                // Create metadata string
                StringBuilder metadata = new StringBuilder();
                metadata.append("docno: " + docno + "\n" +
                        "internal id: " + internalIdCount.toString() + "\n" +
                        "date: " + date + "\n" +
                        "headline: " + headline + "\n" +
                        "raw document:\n");

                // Save gzipped compressed currDoc to currDocFile directory based on date (YY/MM/DD)
                GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(currDocFile));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(gzip, "UTF-8"));
                writer.append(metadata.toString());
                writer.append(currDoc.toString());
                writer.close();

                // Tokenize, convert to term IDs, count words, and update inverted index
                List<String> tokens = tokenize(headline + " " + text + " " + graphic, stem);
                List<Integer> tokenIDs = tokensToIDs(tokens, lexiconTermToID, lexiconIDToTerm);
                HashMap<Integer, Integer> wordCounts = countWords(tokenIDs, writerDocLength);
                addToPostings(wordCounts, internalIdCount, invIndex);

                // Reset variables for the next document
                headline = "";
                text = "";
                graphic = "";

                // Start processing a new document
                if ((currLine = buffered.readLine()) != null) {
                    currDoc = new StringBuilder(); // reset currDoc
                    currLine = buffered.readLine(); // <DOCNO>
                    docno = getDocno(currLine);
                    dateArray = getDate(docno);
                    date = getWordDate(Integer.parseInt(dateArray[0]), Integer.parseInt(dateArray[1]), Integer.parseInt(dateArray[2]));
                    currDoc.append(currLine + "\n"); // append the current line to currDoc
                    internalIdCount = internalIdCount + 1;
                    currDocFile = getDocFilePathFromDocno(docno, dateArray[0], dateArray[1], dateArray[2], metaDataStorePathRoot);
                    writerIndexMap.append(docno + "\n");
                }
            } else {
                currDoc.append(currLine + "\n"); // append current line to doc
            }
        }

        // Save lexicon and inverted index to directory
        FileOutputStream fosLexiconTermToID = new FileOutputStream("latimes_index\\lexiconTermToID.txt");
        ObjectOutputStream oosLexiconTermToID = new ObjectOutputStream(fosLexiconTermToID);
        oosLexiconTermToID.writeObject(lexiconTermToID);
        oosLexiconTermToID.close();

        FileOutputStream fosLexiconIDToTerm = new FileOutputStream("latimes_index\\lexiconIDToTerm.txt");
        ObjectOutputStream oosLexiconIDToTer = new ObjectOutputStream(fosLexiconIDToTerm);
        oosLexiconIDToTer.writeObject(lexiconIDToTerm);
        oosLexiconIDToTer.close();

        FileOutputStream fosInvIndex = new FileOutputStream("latimes_index\\invIndex.txt");
        ObjectOutputStream oosInvIndex = new ObjectOutputStream(fosInvIndex);
        oosInvIndex.writeObject(invIndex);
        oosInvIndex.close();
    }

    // Main method for indexing documents
    public static void main(String[] args) throws FileNotFoundException, FileAlreadyExistsException, IOException {

        // Check if correct number of arguments is provided
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Please include a valid path to the gzip'd data file, " +
                            "a path to store the metadata, and specify third argument as " +
                            "0 for no stem or 1 for stem. For example: " +
                            "'java IndexEngine.java data\\latimes.gz latimes_index 0'"
            );
        }

        // Check if data path exists
        String dataPath = args[0];
        File data = new File(dataPath);
        if (!data.exists()) {
            throw new FileNotFoundException(data + " path does not exist");
        }

        // Check if meta data path already exists
        String metaDataStorePathRoot = args[1];
        if (new File(metaDataStorePathRoot).exists()) {
            throw new FileAlreadyExistsException(metaDataStorePathRoot + " path already exists");
        }

        // Check if stem argument is valid
        String stem = args[2];
        if ((!stem.equals("0") && !stem.equals("1"))) {
            throw new IllegalArgumentException("Third argument must be either: 0 (no index stemming) or 1 (with index stemming)");
        }

        // Create directory for the meta data store
        new File(metaDataStorePathRoot).mkdirs();

        // Create file to store internal and docno
        File indexMap = new File("latimes_index\\indexMap.txt");
        if (!indexMap.createNewFile()) {
            throw new FileAlreadyExistsException("indexMap.txt path already exists");
        }
        FileWriter writerIndexMap = new FileWriter(indexMap);

        // Create file to store document lengths
        File docLength = new File("latimes_index\\doc-lengths.txt");
        if (!docLength.createNewFile()) {
            writerIndexMap.close();
            throw new FileAlreadyExistsException("doc-lengths.txt path already exists");
        }
        FileWriter writerDocLength = new FileWriter(docLength);

        // Read the gzip'd data file
        InputStream fileStream = new FileInputStream(dataPath);
        InputStream gzipStream = new GZIPInputStream(fileStream);
        Reader decoder = new InputStreamReader(gzipStream);
        BufferedReader buffered = new BufferedReader(decoder);

        // Index the file
        indexFile(buffered, metaDataStorePathRoot, writerIndexMap, writerDocLength, stem);

        // Close the file writers and buffered reader
        buffered.close();
        writerIndexMap.close();
        writerDocLength.close();
    }
}
