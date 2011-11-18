/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.similarity;

import org.aksw.sparql2nl.queryprocessing.Query;
import simpack.accessor.graph.SimpleGraphAccessor;
import simpack.measure.graph.GraphIsomorphism;
import simpack.measure.graph.SubgraphIsomorphism;

/**
 *
 * @author ngonga
 */
public class NormedGraphIsomorphism implements QuerySimilarity {

    /** Computes size of small graph isomorphism and norms it with
     * size of graphs
     * @param q1 First query
     * @param q2 Second query
     * @return Similarity
     */
    @Override
    public double getSimilarity(Query q1, Query q2) {
        SimpleGraphAccessor g1 = q1.getGraphRepresentation();
        SimpleGraphAccessor g2 = q2.getGraphRepresentation();
        GraphIsomorphism gi = new GraphIsomorphism(g1, g2);
        SubgraphIsomorphism si = new SubgraphIsomorphism(g1, g2);
        Double sim = si.getSimilarity();
        if (sim != null) {
            return sim;
        } else {
            return 0;
        }
    }
}
