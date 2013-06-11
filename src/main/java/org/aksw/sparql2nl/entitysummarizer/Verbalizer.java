/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import com.hp.hpl.jena.graph.Triple;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.aksw.sparql2nl.entitysummarizer.rules.ObjectMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.PredicateMergeRule;
import org.aksw.sparql2nl.entitysummarizer.rules.Rule;
import org.aksw.sparql2nl.entitysummarizer.rules.SubjectMergeRule;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.dllearner.kb.sparql.SparqlEndpoint;
import simplenlg.framework.NLGElement;
import simplenlg.phrasespec.SPhraseSpec;

/**
 * A verbalizer for triples without variables.
 * @author ngonga
 */
public class Verbalizer {
    SimpleNLGwithPostprocessing nlg;
    
    public Verbalizer(SparqlEndpoint endpoint)
    {
        nlg = new SimpleNLGwithPostprocessing(endpoint);
    }
    
    /** Generates sentence for a given set of triples
     * 
     * @param triples A set of triples
     * @return A set of sentences representing these triples
     */
    public List<NLGElement> verbalize(Set<Triple> triples)
    {
        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        for(Triple t: triples)
        {
            phrases.add(verbalize(t));
        }
        int newSize = phrases.size(), oldSize = phrases.size()+1;
        Rule mr = new ObjectMergeRule();
        Rule pr = new PredicateMergeRule();
        
        //fix point iteration for object and predicate merging
        while(newSize < oldSize)
        {
            oldSize = newSize;
            int mrCount = mr.isApplicable(phrases);
            int prCount = pr.isApplicable(phrases);
            if(prCount > mrCount)
                phrases = pr.apply(phrases);
            else
                phrases = mr.apply(phrases);
            newSize = phrases.size();
        }
        return (new SubjectMergeRule()).apply(phrases);
    }
   
    /** Generates a simple phrase for a triple
     * 
     * @param triple A triple
     * @return A simple phrases
     */
    public SPhraseSpec verbalize(Triple triple)
    {
        return nlg.getNLForTriple(triple);
    }
}
