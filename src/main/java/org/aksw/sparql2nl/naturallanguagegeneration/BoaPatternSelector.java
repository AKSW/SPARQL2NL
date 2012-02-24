package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BinaryRequestWriter;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

/**
 * 
 * @author Daniel Gerber <dgerber@informatik.uni-leipzig.de>
 */
public class BoaPatternSelector {
    
    private static CommonsHttpSolrServer server;
    private static Double WORDNET_DISTANCE_BOOST_FACTOR = 10D;
    private static Double BOA_SCORE_BOOST_FACTOR = 20D;
    private static Double REVERB_BOOST_FACTOR = 5D;
    
    static {
        
        try {
            
            server = new CommonsHttpSolrServer("http://dbpedia.aksw.org:8080/solr/boa_detail");
            server.setRequestWriter(new BinaryRequestWriter());
        }
        catch (MalformedURLException e) {
            
            System.out.println("CommonsHttpSolrServer could not be created");
            e.printStackTrace();
        }
    }

    /**
     * Returns an ordered list of natural language representations for a given 
     * property uri. The list is ordered from highest first to lowest.
     * 
     * @param propertyUri
     * @return
     */
    public static List<String> getNaturalLanguageRepresentation(String propertyUri, int numberOfResults) {
        
        // query the index to get all useful patterns
        List<Pattern> patterns = BoaPatternSelector.querySolrIndex(propertyUri);
        
        // calculate their score for the natural language generation task
        for ( Pattern pattern :  patterns)
            pattern.naturalLanguageScore = calculateNaturalLanguageScore(pattern);
        
        // sort them by the score
        Collections.sort(patterns, new Comparator<Pattern>() {

            public int compare(Pattern pattern1, Pattern pattern2) {

                double x = (pattern2.naturalLanguageScore - pattern1.naturalLanguageScore);
                if ( x < 0 ) return -1;
                if ( x == 0 ) return 0;
                return 1;
            }
        });
        
        // prepare the results in an ordered way (highest to lowest) and only the first $numberOfResults natural language representations
        List<String> results = new ArrayList<String>();
        for ( Pattern pattern : patterns ) {
            
            if ( results.size() >= numberOfResults ) break;
            results.add(pattern.naturalLanguageRepresentation);
        }
        
        return results;
    }

    /**
     * 
     * @param pattern
     * @return
     */
    private static Double calculateNaturalLanguageScore(Pattern pattern) {

        return      REVERB_BOOST_FACTOR             * pattern.features.get("REVERB") 
                +   WORDNET_DISTANCE_BOOST_FACTOR   * pattern.features.get("WORDNET_DISTANCE") 
                +   BOA_SCORE_BOOST_FACTOR          * pattern.boaScore;
    }

    /**
     * Returns all patterns from the index and their features for reverb
     * and the wordnet distance and the overall boa-boaScore. 
     * 
     * @param propertyUri
     * @return a list of patterns
     */
    private static List<Pattern> querySolrIndex(String propertyUri) {

        List<Pattern> patterns = new ArrayList<Pattern>();
        
        try {
            
            SolrQuery query = new SolrQuery("uri:\""+propertyUri+"\"");
            query.addField("REVERB");
            query.addField("WORDNET_DISTANCE");
            query.addField("boa-score");
            query.addField("nlr-no-var");
            query.setRows(100000);
            QueryResponse response = server.query(query);
            SolrDocumentList docList = response.getResults();
            
            // return the first list of types
            for (SolrDocument d : docList) {
                
                Pattern pattern = new Pattern();
                pattern.naturalLanguageRepresentation = (String) d.get("nlr-no-var");
                pattern.features.put("REVERB", Double.valueOf((String) d.get("REVERB")));
                pattern.features.put("WORDNET_DISTANCE", Double.valueOf((String) d.get("WORDNET_DISTANCE")));
                pattern.boaScore = Double.valueOf((String) d.get("boa-score"));
                
                if ( !patterns.contains(pattern) ) patterns.add(pattern);
            }
        }
        catch (SolrServerException e) {
            
            System.out.println("Could not execute query: " + e);
            e.printStackTrace();
        }
        return patterns;
    }
    
    public static void main(String[] args) throws IOException {
        
        blubb();
        

        int i = 1;
//        for ( String s : BoaPatternSelector.getNaturalLanguageRepresentation("http://dbpedia.org/ontology/spouse", 10)) {
//            
//            System.out.println(i++ + ". " + s);
//        }
    }
    
    private static void blubb() throws IOException {
        
        String filePath = "resources/qald2-dbpedia-train.xml";
        byte[] buffer = new byte[(int) new File(filePath).length()];
        BufferedInputStream f = null;
        try {
            f = new BufferedInputStream(new FileInputStream(filePath));
            f.read(buffer);
        } finally {
            if (f != null) try { f.close(); } catch (IOException ignored) { }
        }
        String queryString = new String(buffer);
        
        Map<String,Integer> distribution = new HashMap<String,Integer>();
        Matcher matcher = java.util.regex.Pattern.compile("db[op]:\\p{Lower}\\w+\\s").matcher(queryString);
        while (matcher.find()) {
            
            String property = matcher.group();
            if ( distribution.containsKey(property) ) distribution.put(property, distribution.get(property) + 1);
            else distribution.put(property, 1);
        }
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            
            result.add(entry.getValue() + ": " + entry.getKey());
        }
        Collections.sort(result);
        for ( String s : result ) System.out.println(s);
    }
    
    /**
     * Only used inside this class to encapsulate the Solr query results.
     */
    private static class Pattern {
        
        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {

            final int prime = 31;
            int result = 1;
            result = prime * result + ((naturalLanguageRepresentation == null) ? 0 : naturalLanguageRepresentation.hashCode());
            return result;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {

            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Pattern other = (Pattern) obj;
            if (naturalLanguageRepresentation == null) {
                if (other.naturalLanguageRepresentation != null)
                    return false;
            }
            else
                if (!naturalLanguageRepresentation.equals(other.naturalLanguageRepresentation))
                    return false;
            return true;
        }

        Map<String,Double> features = new HashMap<String,Double>();
        String naturalLanguageRepresentation = "";
        Double boaScore = 0D;
        Double naturalLanguageScore = 0D;
        
        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {

            StringBuilder builder = new StringBuilder();
            builder.append("Pattern [features=");
            builder.append(features);
            builder.append(", naturalLanguageRepresentation=");
            builder.append(naturalLanguageRepresentation);
            builder.append(", boaScore=");
            builder.append(boaScore);
            builder.append(", naturalLanguageScore=");
            builder.append(naturalLanguageScore);
            builder.append("]");
            return builder.toString();
        }
    }
}
