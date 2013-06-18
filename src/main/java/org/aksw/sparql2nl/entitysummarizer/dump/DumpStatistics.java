/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.dump;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author ngonga
 */
public class DumpStatistics {
    // 1. Get statistics on properties ergo Map<Node, Map<Set<Node>, Integer>>> 
    // maps each class to a map that contains property pairs and the frequency 
    // with which they occur
    
    public Set<Set<Node>> clusterProperties(Map<Set<Node>, Integer> properties)
    {
        return new HashSet<Set<Node>>();
        
//        Model m = ModelFactory.createDefaultModel();
//        Property m.createProperty(null, null);
    }
}
