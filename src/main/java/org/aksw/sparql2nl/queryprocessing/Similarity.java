/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.queryprocessing;

import org.aksw.sparql2nl.similarity.NormedGraphIsomorphism;
import org.aksw.sparql2nl.similarity.TypeAwareGraphIsomorphism;
import uk.ac.shef.wit.simmetrics.similaritymetrics.*;

/**
 * Computes the similarity between two queries
 * @author ngonga
 */
public class Similarity {

	public enum SimilarityMeasure{
		LEVENSHTEIN, QGRAMS, JACCARD, GRAPH_ISOMORPHY, TYPE_AWARE_ISOMORPHY
	}

    public static double getSimilarity(Query q1, Query q2, SimilarityMeasure measure)
    {
        //check whether queries use the same features
        //if they don't return 0
        if(q1.getUsesCount() != q2.getUsesCount() ||
                q1.getUsesGroupBy() != q2.getUsesGroupBy() ||
                q1.getUsesLimit() != q2.getUsesLimit() ||
                q1.getUsesSelect() != q2.getUsesSelect()) return 0;
        //if they do then compute similarity
        if(measure == SimilarityMeasure.LEVENSHTEIN)
            return (new Levenshtein()).getSimilarity(q1.getQueryWithOnlyVars(), q2.getQueryWithOnlyVars());
        else if(measure == SimilarityMeasure.QGRAMS)
            return (new QGramsDistance()).getSimilarity(q1.getQueryWithOnlyVars(), q2.getQueryWithOnlyVars());
        else if(measure == SimilarityMeasure.JACCARD)
            return (new JaccardSimilarity()).getSimilarity(q1.getQueryWithOnlyVars(), q2.getQueryWithOnlyVars());
        //add graph based-metric here
        else if(measure == SimilarityMeasure.GRAPH_ISOMORPHY)
            return (new NormedGraphIsomorphism().getSimilarity(q1, q2));
        else if(measure == SimilarityMeasure.TYPE_AWARE_ISOMORPHY)
            return (new TypeAwareGraphIsomorphism().getSimilarity(q1, q2));
        //default
        return (new Levenshtein()).getSimilarity(q1.getQueryWithOnlyVars(), q2.getQueryWithOnlyVars());
    }
}
