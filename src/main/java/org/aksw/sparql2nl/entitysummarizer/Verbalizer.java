/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import com.hp.hpl.jena.graph.Triple;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.dllearner.kb.sparql.SparqlEndpoint;
import simplenlg.framework.NLGElement;
import simplenlg.phrasespec.SPhraseSpec;

/**
 *
 * @author ngonga
 */
public class Verbalizer {
    SimpleNLGwithPostprocessing nlg;
    
    public Verbalizer(SparqlEndpoint endpoint)
    {
        nlg = new SimpleNLGwithPostprocessing(endpoint);
    }
    
    public NLGElement verbalize(Set<Triple> triples)
    {
        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        for(Triple t: triples)
        {
            phrases.add(verbalize(t));
        }
        return null;
    }
   
    
    public SPhraseSpec verbalize(Triple triple)
    {
        return nlg.getNLForTriple(triple);
    }
}
