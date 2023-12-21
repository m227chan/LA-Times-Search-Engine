// Author: Matthew Chan modified code provided by Mark D. Smucker

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;

public class RelevanceJudgements
{
    // Internal class for use by RelevanceJudgments to hold the judgements
    private class Tuple
    {
        public Tuple(String queryID, String docID, int relevant)
        {
            _queryID = queryID ;
            _docID = docID ;
            _relevant = relevant ;
        }
        private String _queryID ;
        private String _docID ;
        private int _relevant ;

        public String queryID()
        {
            return _queryID;
        }
        public String docID() 
        {
            return _docID;
        }
        public int relevant() 
        {
            return _relevant;
        }

        public static String GenerateKey( String queryID, String docID )
        {
            return queryID + "-" + docID ;
        }

        public String Key()
        {
            return _queryID + "-" + _docID ;
        }
    }

    private Hashtable<String, Tuple> tuples = new Hashtable<>();
    private Hashtable<String, ArrayList<String>> query2reldocnos = new Hashtable<>();
    
    public void RelevanceJudgments()
    {
        this.tuples = new Hashtable<String, Tuple>() ;
        this.query2reldocnos = new Hashtable<String, ArrayList<String>>() ;
    }
    
    public void AddJudgement( String queryID, String docID, int relevant ) throws Exception
    {
        Tuple tuple = new Tuple( queryID, docID, relevant ) ;
        if ( tuples.containsKey( tuple.Key() ) ) {
            throw new Exception( "Cannot have duplicate queryID and docID data points" ) ;
        }
        tuples.put( tuple.Key(), tuple ) ;
        if ( tuple.relevant() != 0 )
        {
            // store the reldocnos
            ArrayList<String> tmpRelDocnos = null;
            if ( query2reldocnos.containsKey( queryID ) )
            {
                tmpRelDocnos = query2reldocnos.get(queryID);
            }
            else
            {
                tmpRelDocnos = new ArrayList<>();
                query2reldocnos.put( queryID, tmpRelDocnos );
            }
            if ( !tmpRelDocnos.contains( docID ) )
                tmpRelDocnos.add( docID );
        }
    }

    // Is the document relevant to the query?
    public Boolean IsRelevant( String queryID, String docID ) throws Exception
    {
        return GetJudgement( queryID, docID, true ) != 0 ;
    }

    public int GetJudgement( String queryID, String docID ) throws Exception
    {
        return GetJudgement( queryID, docID, false ) ;
    }

    public int GetJudgement( String queryID, String docID, Boolean assumeNonRelevant ) throws Exception
    {
        if ( ! query2reldocnos.containsKey( queryID ) )
            throw new Exception( "no relevance judgments for queryID = " + queryID ) ;

        String key = Tuple.GenerateKey( queryID, docID ) ;
        if ( ! tuples.containsKey( key ) )
        {
            if ( assumeNonRelevant )
                return 0 ;
            else
                throw new Exception( "no relevance judgement for queryID and docID" ) ;
        }
        else
        {
            Tuple tuple = tuples.get(key) ;
            return tuple.relevant();
        }
    }

    // Number of relevant documents in collection for query
    public int NumRelevant( String queryID ) throws Exception
    {
        if ( query2reldocnos.containsKey( queryID ) )
            return (query2reldocnos.get(queryID)).size() ;
        else
            throw new Exception( "no relevance judgments for queryID = " + queryID ) ;
    }

    // returns the queryID Strings
    public ArrayList<String> QueryIDs()
    {
        ArrayList<String> queryIdList = new ArrayList<>(query2reldocnos.keySet());
        Collections.sort(queryIdList);
        return queryIdList;
    }

    public ArrayList<String> RelDocnos( String queryID ) throws Exception
    {
        if ( query2reldocnos.containsKey( queryID ) )
            return query2reldocnos.get(queryID) ;
        else
            throw new Exception( "no relevance judgments for queryID = " + queryID ) ;
    }
}
