/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.statistics;
import java.util.Map;
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
/**
 *
 * @author ngonga
 */
public interface Stats {
    Map<? extends Node, Double> computeSignificance(Map<? extends Node, Double> edges);
}
