// Author: Matthew Chan modified code provided by Mark D. Smucker

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class QRels
{
    // The results of reading in the file
    public RelevanceJudgements judgments = new RelevanceJudgements() ;

    // This will throw IO exceptions if something IO bad happens
    public QRels( String fullPath ) throws FileNotFoundException, IOException, Exception
    {
        BufferedReader sr = new BufferedReader( new FileReader(fullPath) ) ;
        String line ;
        while ( (line = sr.readLine()) != null )
        {
            String[] fields = line.split(" ") ;
            // should be "query-num unknown doc-id rel-judgment"
            if ( fields.length != 4 )
            {
                throw new Exception( "input should have 4 columns" ) ;
            }
            String queryID = fields[0] ;
            String docID = fields[2] ;
            int relevant = Integer.parseInt( fields[3] ) ;
            judgments.AddJudgement( queryID, docID, relevant ) ;
        }
        sr.close() ;
    }
}