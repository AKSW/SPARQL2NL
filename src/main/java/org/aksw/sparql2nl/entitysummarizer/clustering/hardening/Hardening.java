/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.clustering.hardening;

import java.util.List;
import java.util.Set;
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;

/**
 *
 * @author ngonga
 */
public interface Hardening {
    List<Set<Node>> harden(Set<Set<Node>> clusters, WeightedGraph wg);
}
