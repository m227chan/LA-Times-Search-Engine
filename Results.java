// Author: Matthew Chan modified code provided by Mark D. Smucker

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;

public class Results {
    public class Result implements Comparable<Result> {
        public Result(String docID, Double score, int rank)
        {
            this._docID = docID ;
            this._score = score ;
            this._rank = rank ;
        }

        private String _docID ;
        private Double _score ;
        private int _rank ;

        public String docID()
        {
            return _docID ;
        }
        public Double score()
        {
            return _score ;
        }

        public int rank()
        {
            return _rank ;
        }

        // For IComparable, we'll sort from high to low score,
        // if the scores are the same, then we sort from high docno to low docno
        // This is what TREC eval does.  Checked on trec 9 web to work.
        // as of 10/14/2011, I think sorting from high to low docno may be 
        // backwards. 
        // Okay, this is what trec_eval does (as far as I can tell):
        //static int
        //comp_sim_docno (ptr1, ptr2)
        //TEXT_TR *ptr1;
        //TEXT_TR *ptr2;
        //{
        //    if (ptr1->sim > ptr2->sim)
        //        return (-1);
        //    if (ptr1->sim < ptr2->sim)
        //        return (1);
        //    return (strcmp (ptr2->docno, ptr1->docno));
        //}
        // 
        // so that is a descending sort on score and docno 
        //
        @Override public int compareTo(Result obj)
        {
            Result rhs = (Result) obj ;
            Result lhs = this ;
            int scoreCompare = -1 * lhs.score().compareTo( rhs.score() ) ;
            if ( scoreCompare == 0 )
            {
                return -1 * lhs.docID().compareTo( rhs.docID() ) ;
            }
            else
            {
                return scoreCompare ;
            }
        }
    }

    /// holds keys of queryID and docID to make sure no dupes are added
    private HashMap<String, String> tupleKeys = new HashMap<>();
    
    /// keyed by queryID to an ArrayList of the queries' results.
    private HashMap<String, ArrayList<Result>> query2results = new HashMap<>(); 
    private HashMap<String, Boolean> query2isSorted = new HashMap<>();
    
    public Results()
    {
        this.tupleKeys = new HashMap<>() ;
        this.query2results = new HashMap<>() ;
        this.query2isSorted = new HashMap<>() ;
    }

    public void AddResult( String queryID, String docID, Double score, int rank ) throws Exception
    {
        // be a bit careful about catching a bad mistake
        String key = this.GenerateTupleKey( queryID, docID ) ;
        if ( this.tupleKeys.containsKey( key ) )
            throw new Exception( "Cannot have duplicate queryID and docID data points" ) ;
        this.tupleKeys.put( key, null ) ;

        // Add to database
        ArrayList<Result> results = null ;
        if ( this.query2results.containsKey( queryID ) )
        {
            results = (ArrayList<Result>) this.query2results.get(queryID) ;
        }
        else
        {
            results = new ArrayList<>() ;
            this.query2results.put( queryID, results ) ;
            this.query2isSorted.put( queryID, false ) ; 
        }
        Result result = new Result( docID, score, rank ) ;
        results.add( result ) ;
    }

    public String GenerateTupleKey( String queryID, String docID )
    {
        return queryID + "-" + docID ;
    }

    // Returns the results for queryID sorted by score
    public ArrayList<Result> QueryResults( String queryID ) throws Exception
    {
        ArrayList<Result> results = (ArrayList<Result>) this.query2results.get(queryID) ;
        if (results == null) {
            return null;
        } else {
            Collections.sort(results, new CustomComparator());
        }
        return results ;
    }

    public class CustomComparator implements Comparator<Result> {
        @Override
        public int compare(Result result1, Result result2) {
            int scoreComparison = Double.compare(result2.score(), result1.score());
            if (scoreComparison == 0) {
                // if tie, then sort by descending lexicographical order of docno
                return result2.docID().compareTo(result1.docID());
            } else {
                // not tie, then sort by score
                return scoreComparison;
            }
        }
    }

    // returns the collection of QueryIDs 
    public Set<String> QueryIDs()
    {
        return this.query2results.keySet();
    }

    public Boolean QueryIDExists( String queryID )
    {
        return this.query2results.containsKey( queryID ) ;
    }
}