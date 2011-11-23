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
	        gi.calculate();
	        if(gi.getGraphIsomorphism() == 1)
	        {
	            return 0.5 + 0.5*directionalSimilarity(g1, g2, gi.getCliqueList());
	        }
	        else System.out.println("Graphs are not isomorph");

	        SubgraphIsomorphism si = new SubgraphIsomorphism(g1, g2);
	        Double sim = si.getSimilarity();
	        if (sim != null) {
	            return 0.5*sim;
	        } else {
	            return 0;
	        }
	    }

	    private double directionalSimilarity(SimpleGraphAccessor g1, SimpleGraphAccessor g2, TreeSet<String> cliqueList) {
	        HashMap<String, String> nodeMapping = new HashMap<String, String>();
	        for(String s: cliqueList)
	        {
	            String[] split1 = s.split(Pattern.quote(", "));
	            for(int i=0; i<split1.length; i++)
	            {
	                String entry = split1[i];
	            String[] split = entry.split(Pattern.quote(":"));
	            nodeMapping.put(split[0], split[1]);
	            }
	        }

	        //get successor map at string level
	        HashMap<String, TreeSet<String>> successors1 = new HashMap<String, TreeSet<String>>();
	        double edgeCount1 = 0;
	        for(IGraphNode n: g1.getNodeSet())
	        {
	            TreeSet<IGraphNode> succ = n.getSuccessorSet();
	            TreeSet<String> labels = new TreeSet<String>();
	            for(IGraphNode ns: succ)
	            {
	                labels.add(ns.getLabel());
	                edgeCount1++;
	            }
	            successors1.put(n.getLabel(), labels);
	        }

	        HashMap<String, TreeSet<String>> successors2 = new HashMap<String, TreeSet<String>>();
	        double edgeCount2 = 0;
	        for(IGraphNode n: g2.getNodeSet())
	        {
	            TreeSet<IGraphNode> succ = n.getSuccessorSet();
	            TreeSet<String> labels = new TreeSet<String>();
	            for(IGraphNode ns: succ)
	            {
	                labels.add(ns.getLabel());
	                edgeCount2++;
	            }
	            successors2.put(n.getLabel(), labels);
	        }

	        //now compare common edges
	        double count = 0;
	        for(String node1: nodeMapping.keySet())
	        {
	            String node2 = nodeMapping.get(node1);
	            TreeSet<String> succ2 = successors2.get(node2);
	            for(String succ1: successors1.get(node1))
	            {
	                if(succ2.contains(nodeMapping.get(succ1)))
	                    count++;
	            }
	        }

	        return 2*count/(edgeCount1 + edgeCount2);
    }
}
