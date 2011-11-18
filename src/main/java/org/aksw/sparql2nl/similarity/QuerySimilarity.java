/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.similarity;

import org.aksw.sparql2nl.queryprocessing.Query;

/**
 *
 * @author ngonga
 */
public interface QuerySimilarity {
    public double getSimilarity(Query q1, Query q2);
}
