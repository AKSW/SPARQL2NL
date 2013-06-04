/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.statistics;

import java.util.HashMap;
import java.util.Map;
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;

/**
 *
 * @author ngonga
 */
public class ChiSquaredStats implements Stats {
    
    public Map<? extends Node, Double> computeSignificance(Map<? extends Node, Double> edges)
    {
        double expected = 0d;
        for(Node n: edges.keySet())
        {
            expected = expected + edges.get(n);
        }
        expected = expected/edges.keySet().size();
        
        Map<Node, Double> result = new HashMap<Node, Double>();
        for(Node n: edges.keySet())
        {
            result.put(n, Math.pow(edges.get(n)-expected, 2)/expected);
        }
        return result;
    }
    
}
