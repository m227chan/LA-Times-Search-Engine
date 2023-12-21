// Author: Matthew Chan modified code provided by Mark D. Smucker

import java.io.BufferedReader;
import java.io.FileReader;

public class ResultsFile 
{
    public Results results = new Results() ;
    public String runID ;

    public static boolean isNumeric(String str) { 
        try {  
            Double.parseDouble(str);  
            return true;
        } catch(NumberFormatException e){  
            return false;  
        }  
    }

    public ResultsFile( String fullPath ) throws Exception
    {
        BufferedReader sr = new BufferedReader( new FileReader(fullPath) ) ;
        Boolean firstLine = true ;
        String line ;
        while ( (line = sr.readLine()) != null )
        {
            String [] fields = line.split(" ") ;
            // should be "queryID Q0 doc-id rank score runID"
            if ( fields.length != 6 )
            {
                sr.close();
                throw new Exception( "input should have 6 columns" ) ;
            }

            for (int i = 0; i < fields.length; i++) {
                // catch null values
                if (fields[i].equals("null")) {
                    sr.close();
                    throw new Exception("null value in the fields" ) ;
                }
            }

            if (!isNumeric(fields[3]) || !isNumeric(fields[4])) {
                sr.close();
                throw new Exception("bad format: non-numeric value in rank or score fields" ) ;
            }
            String queryID = fields[0] ;
            String docID = fields[2] ;
            int rank = Integer.parseInt( fields[3] ) ;
            Double score = null;
            score = Double.parseDouble( fields[4] ) ;
            results.AddResult( queryID, docID, score, rank ) ;
            if ( firstLine )
            {
                this.runID = fields[5] ;
                firstLine = false ; 
            }
            else if ( !this.runID.equals(fields[5]) )
            {
                sr.close();
                throw new Exception("mismatching runIDs in file" ) ;
            }
        }
        sr.close() ;
    }
}
