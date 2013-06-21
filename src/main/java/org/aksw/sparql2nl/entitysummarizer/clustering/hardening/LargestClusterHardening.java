/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.clustering.hardening;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.aksw.sparql2nl.entitysummarizer.clustering.BorderFlowX;
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.aksw.sparql2nl.entitysummarizer.clustering.hardening.Hardening;

/**
 * Hardening that prefers clusters with larger weight
 * @author ngonga
 */
public class LargestClusterHardening implements Hardening{

    public List<Set<Node>> harden(Set<Set<Node>> clusters, WeightedGraph wg) {
        Set<Node> nodes = new HashSet<Node>(wg.getNodes().keySet());
        double max, weight;
        Set<Node> bestCluster;
        List<Set<Node>> result = new ArrayList<Set<Node>>();
        while (!nodes.isEmpty()) {
            max = 0d;
            bestCluster = null;
            //first get weights            
            for (Set<Node> c : clusters) {
                if (!result.contains(c)) {
                    weight = getWeight(c, wg, nodes);
                    System.out.println(c +" -> "+weight);
                    if (weight > max) {
                        max = weight;
                        bestCluster = c;
                    }
                }
            }
            // no more clusters available
            if (bestCluster == null) {
                return result;
            }
            //in all other cases
            clusters.remove(bestCluster);
            bestCluster.retainAll(nodes);
            result.add(bestCluster);
            nodes.removeAll(bestCluster);
        }
        return result;
    }

    /**
     * Computes the weight of a cluster w.r.t. to a given set of nodes within a
     * weighted graph
     *
     * @param cluster A cluster
     * @param wg A node- and edge-weighted graph
     * @param reference
     * @return Weight of the set of nodes
     */
    public double getWeight(Set<Node> cluster, WeightedGraph wg, Set<Node> reference) {
        double w = 0d;

        for (Node n : cluster) {
            if (reference.contains(n)) {
                for (Node n2 : cluster) {
                    if (reference.contains(n2)) {
                        if (n.equals(n2)) {
                            w = w + wg.getNodeWeight(n);
                        } else {
                            w = w + wg.getEdgeWeight(n, n2);
                        }
                    }
                }
            }
        }
        return w;
    }

    public static void main(String args[]) {
        WeightedGraph wg = new WeightedGraph();
        Node n1 = wg.addNode("a", 2.0);
        Node n2 = wg.addNode("b", 2.0);
        Node n3 = wg.addNode("c", 2.0);
        Node n4 = wg.addNode("d", 4.0);
        wg.addEdge(n1, n2, 1.0);
        wg.addEdge(n2, n3, 1.0);
        wg.addEdge(n2, n4, 1.0);

        BorderFlowX bf = new BorderFlowX(wg);
        Set<Set<Node>> clusters = bf.cluster();
//        System.out.println(clusters +"=>"+(new LargestClusterHardening()).harden(clusters, wg));
        System.out.println(clusters +"=>"+(new AverageWeightClusterHardening()).harden(clusters, wg));
    }
}
