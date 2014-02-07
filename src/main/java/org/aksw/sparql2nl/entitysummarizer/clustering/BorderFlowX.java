/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.clustering;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 *
 * @author ngonga
 */
public class BorderFlowX implements ClusteringAlgorithm {
	
	private static final Logger logger = Logger.getLogger(BorderFlowX.class.getName());

    WeightedGraph graph;

    public BorderFlowX(WeightedGraph wg) {
        graph = wg;
    }

    public void setGraph(WeightedGraph wg) {
        graph = wg;
    }

    public Set<Set<Node>> cluster(WeightedGraph wg) {
        graph = wg;
        return cluster();
    }

    public Set<Set<Node>> cluster() {
        Set<Set<Node>> result = new HashSet<Set<Node>>();
        logger.debug("Graph ===\n"+graph);
        for (Node n : graph.nodes.keySet()) {
            result.add(cluster(n));
        }
        return result;
    }

    public Set<Node> cluster(Node n) {
        if (!graph.nodes.containsKey(n)) {
            return null;
        }

        //initial borderflow
        double oldBorderFlow, newBorderFlow = graph.getNodeWeight(n);

        //initial cluster
        Set<Node> cluster = new HashSet<Node>();
        cluster.add(n);

        do {
            // set old flow
            oldBorderFlow = newBorderFlow;
            // compute candidates
            Set<Node> candidates = getCandidates(cluster, oldBorderFlow);
            // if candidates exist
            if (!candidates.isEmpty()) {
                cluster.addAll(candidates);
                newBorderFlow = computeBorderFlowRatio(cluster);
            }
        } while (oldBorderFlow < newBorderFlow);
        logger.debug("Cluster("+n.label+") = "+cluster);
        return cluster;
    }

    /**
     * Computes the border flow ratio for cluster + addition. Used to check what
     * would happen if addition was inserted in the cluster
     *
     * @param cluster A cluster
     * @param addition Node to be added to the cluster
     * @return Border flow ratio of cluster when addition is added to it
     */
    public double computeBorderFlowRatio(Set<Node> cluster, Node addition) {
        Set<Node> newCluster = new HashSet<Node>();
        for (Node n : cluster) {
            newCluster.add(n);
        }
        newCluster.add(addition);
        return computeBorderFlowRatio(newCluster);
    }

    /**
     * Computes the extended border flow ratio for a given cluster
     *
     * @param cluster A cluster
     * @return Its border flow ratio
     */
    public double computeBorderFlowRatio(Set<Node> cluster) {
        //first get border
        Set<Node> border = getBorder(cluster);
        //then get the inner nodes
        Set<Node> innerNodes = new HashSet<Node>(cluster);
        innerNodes.removeAll(border);
        double neighborFlow = getFlow(border, getNeighbors(cluster));
        //ensure no division by zero
        if (neighborFlow == 0) {
            neighborFlow = 1d;
        }
        double w = getWeight(border);
        double f = getFlow(border, cluster);
        return (w + f) / neighborFlow;
    }

    /**
     * Computes the total weight of a set of nodes
     *
     * @param nodes A set of nodes
     * @return Total weight
     */
    public double getWeight(Set<Node> nodes) {
        double w = 0;
        for (Node n : nodes) {
            w = w + graph.getNodeWeight(n);
        }
        return w;
    }

    /**
     * Computes the total flow from a set of source nodes to a set of target
     * nodes
     *
     * @param source Set of source nodes
     * @param target Set of target nodes
     * @return Total flow (0 if node)
     */
    public double getFlow(Set<Node> source, Set<Node> target) {
        double flow = 0d;
        for (Node s : source) {
            for (Node t : target) {
                flow = flow + graph.getEdgeWeight(s, t);
            }
        }
        return flow;
    }

    /**
     * Computes the neighbors of a set of nodes
     *
     * @param nodes A set of nodes
     * @return Its neighbors
     */
    public Set<Node> getNeighbors(Set<Node> nodes) {
        Set<Node> results = new HashSet<Node>();
        for (Node n : nodes) {
            Set<Node> neighbors = graph.getNeighbors(n);
            for (Node m : neighbors) {
                if (!nodes.contains(m)) {
                    results.add(m);
                }
            }
        }
        return results;
    }

    /**
     * Computes the border of a set of nodes
     *
     * @param nodes A set of nodes
     * @return The border of the set
     */
    public Set<Node> getBorder(Set<Node> nodes) {
        Set<Node> results = new HashSet<Node>();
        for (Node n : nodes) {
            Set<Node> neighbors = graph.getNeighbors(n);
            for (Node m : neighbors) {
                if (!nodes.contains(m)) {
                    //means n points to an m which is outside the cluster
                    results.add(n);
                }
            }
        }
        return results;
    }

    /**
     * Computes the inner nodes of a set of nodes
     *
     * @param nodes A set of nodes
     * @return The inner nodes
     */
    public Set<Node> getInnerNodes(Set<Node> nodes) {
        Set<Node> border = getBorder(nodes);
        Set<Node> innerNodes = new HashSet<Node>(nodes);
        innerNodes.removeAll(border);
        return innerNodes;
    }
//    private Set<Node> getCandidates(Set<Node> cluster, Set<Node> neighbors, double borderFlow) {
//        int max = 0;
//        
//    }

    private Set<Node> getCandidates(Set<Node> cluster, double oldBorderFlow) {
        Set<Node> neighbors = getNeighbors(cluster);
        double max = oldBorderFlow;
        double bfr;
        Set<Node> candidates = new HashSet<Node>();
        // first look for candidates that improve the border flow ratio
        for (Node n : neighbors) {
            bfr = computeBorderFlowRatio(cluster, n);
            if (bfr > max) {
                candidates = new HashSet<Node>();
                candidates.add(n);
                max = bfr;
            } else if (bfr == max) {
                candidates.add(n);
            }
        }
        // if no candidates then no need to continue
        if (candidates.isEmpty()) {
            return candidates;
        }
        //return if only one node was found as candidate
        if (candidates.size() == 1) {
            return candidates;
        }
        //else filter
        max = 0;
        Set<Node> finalCandidates = new HashSet<Node>();
        // now look for candidates with maximal flow to the neighborhood
        for (Node n : candidates) {
            Set<Node> c = new HashSet<Node>();
            c.add(n);
            bfr = getFlow(c, candidates);
            if (bfr > max) {
                finalCandidates = new HashSet<Node>();
                finalCandidates.add(n);
                max = bfr;
            } else if (bfr == max) {
                finalCandidates.add(n);
            }
        }
        return finalCandidates;
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
        System.out.println(wg);
        Set<Node> nodes = new HashSet<Node>();
        nodes.add(n2);
        nodes.add(n1);
        
        System.out.println("Border = " + bf.getNeighbors(nodes));
        System.out.println("BFR = " + bf.computeBorderFlowRatio(nodes));
        System.out.println("Cluster = " + bf.cluster(n1));
        System.out.println("Cluster = " + bf.cluster(n2));
        
    }
}
