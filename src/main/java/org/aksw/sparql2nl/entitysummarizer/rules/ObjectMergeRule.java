/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.aksw.sparql2nl.nlp.stemming.PlingStemmer;
import org.dllearner.kb.sparql.SparqlEndpoint;

import simplenlg.features.Feature;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.Triple;

/**
 *
 * @author ngonga
 */
public class ObjectMergeRule implements Rule {

   
	Lexicon lexicon;
    NLGFactory nlgFactory;
    Realiser realiser;

    public ObjectMergeRule(Lexicon lexicon, NLGFactory nlgFactory, Realiser realiser) {
		this.lexicon = lexicon;
		this.nlgFactory = nlgFactory;
		this.realiser = realiser;
	}

    /**
     * Checks whether a rule is applicable and returns the number of pairs on
     * which it can be applied
     *
     * @param phrases List of phrases
     * @return Number of mapping pairs
     */
    public int isApplicable(List<SPhraseSpec> phrases) {
        int max = 0, count = 0;
        SPhraseSpec p1, p2;
        String verb1, verb2;
        String subj1, subj2;

        for (int i = 0; i < phrases.size(); i++) {
            p1 = phrases.get(i);
            count = 0;
            for (int j = i + 1; j < phrases.size(); j++) {
                p2 = phrases.get(j);
                if (p1.getVerb().equals(p2.getVerb()) && p1.getSubject().equals(p2.getSubject())) {
                    count++;
                }
            }
            max = Math.max(max, count);
        }
        return max;
    }
    
    /**
     * Applies this rule to the phrases
     *
     * @param phrases Set of phrases
     * @return Result of the rule being applied
     */
    public List<SPhraseSpec> apply(List<SPhraseSpec> phrases) {
    	return apply(phrases, false);
    }

    /**
     * Applies this rule to the phrases
     *
     * @param phrases Set of phrases
     * @return Result of the rule being applied
     */
    public List<SPhraseSpec> apply(List<SPhraseSpec> phrases, boolean amongOthers) {

        if (phrases.size() <= 1) {
            return phrases;
        }

        SPhraseSpec p1, p2;
        String verb1, verb2;
        String subj1, subj2;

        // get mapping o's
        Multimap<Integer, Integer> map = TreeMultimap.create();
        for (int i = 0; i < phrases.size(); i++) {
            p1 = phrases.get(i);
            verb1 = realiser.realiseSentence(p1.getVerb());
            subj1 = realiser.realiseSentence(p1.getSubject());

            for (int j = i + 1; j < phrases.size(); j++) {
                p2 = phrases.get(j);
                verb2 = realiser.realiseSentence(p2.getVerb());
                subj2 = realiser.realiseSentence(p2.getSubject());

                if (verb1.equals(verb2) && subj1.equals(subj2)) {
                    map.put(i, j);
                }
            }
        }

        int maxSize = 0;
        int phraseIndex = -1;

        //find the index with the highest number of mappings
//        List<Integer> phraseIndexes = new ArrayList<Integer>(map.keySet());
        for (int key : map.keySet()) {
            if (map.get(key).size() > maxSize) {
                maxSize = map.get(key).size();
                phraseIndex = key;
            }
        }
        
        if(phraseIndex == -1) return phrases;
        //now merge
        Collection<Integer> toMerge = map.get(phraseIndex);
        toMerge.add(phraseIndex);
        CoordinatedPhraseElement elt = nlgFactory.createCoordinatedPhrase();
        if(amongOthers){
        	 elt.addPreModifier(", among others,");
        }

        for (int index : toMerge) {
            elt.addCoordinate(phrases.get(index).getObject());
        }

        SPhraseSpec fusedPhrase = phrases.get(phraseIndex);
        fusedPhrase.setObject(elt);
        if (fusedPhrase.getSubject().getChildren().size() > 1) //possessive subject
        {
            for (NLGElement subjElt : fusedPhrase.getSubject().getChildren()) {
                if (!subjElt.hasFeature(Feature.POSSESSIVE)) {
                	System.out.println(((NPPhraseSpec) subjElt).getHead().getRealisation());
                    ((NPPhraseSpec) subjElt).getHead().setPlural(true);
                } 
            }
            //we need to transform the head of the possessive clause into singular, otherwise we could get something like "number of pageses"
            String realisedHead = realiser.realise(((NPPhraseSpec)fusedPhrase.getSubject()).getHead()).getRealisation();
            realisedHead = PlingStemmer.stem(realisedHead);
            ((NPPhraseSpec)fusedPhrase.getSubject()).setHead(nlgFactory.createInflectedWord(realisedHead, LexicalCategory.NOUN));
            
            fusedPhrase.getSubject().setPlural(true);
            fusedPhrase.getVerb().setPlural(true);
        }
        //now create the final result
        List<SPhraseSpec> result = new ArrayList<SPhraseSpec>();
        for (int index = 0; index < phrases.size(); index++) {            
            if (index == phraseIndex) {
                result.add(fusedPhrase);
            }
            else if (!toMerge.contains(index)) {
                result.add(phrases.get(index));
            }
        }
        return result;
    }

    public static void main(String args[]) {
        Lexicon lexicon = Lexicon.getDefaultLexicon();
        NLGFactory nlgFactory = new NLGFactory(lexicon);
        Realiser realiser = new Realiser(lexicon);

        SPhraseSpec s1 = nlgFactory.createClause();
        s1.setSubject("Mike");
        s1.setVerb("like");
        s1.setObject("apples");
        s1.getObject().setPlural(true);

        SPhraseSpec s2 = nlgFactory.createClause();
        s2.setSubject("Mike");
        s2.setVerb("like");
        s2.setObject("banana");
        s2.getObject().setPlural(true);

        SPhraseSpec s3 = nlgFactory.createClause();
        s3.setSubject("John");
        s3.setVerb("like");
        s3.setObject("banana");
        s3.getObject().setPlural(true);

        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        phrases.add(s1);
        phrases.add(s2);
        phrases.add(s3);

        for (SPhraseSpec p : phrases) {
            System.out.println("=>" + realiser.realiseSentence(p));
        }
        phrases = (new ObjectMergeRule(lexicon, nlgFactory, realiser)).apply(phrases);

        for (SPhraseSpec p : phrases) {
            System.out.println("=>" + realiser.realiseSentence(p));
        }
        
        SimpleNLGwithPostprocessing nlg = new SimpleNLGwithPostprocessing(SparqlEndpoint.getEndpointDBpedia());
        SPhraseSpec s4 = nlg.getNLForTriple(Triple.create(
        		NodeFactory.createURI("http://dbpedia.org/resource/Aero_Lloyd"), 
        		NodeFactory.createURI("http://dbpedia.org/ontology/headquarter"), 
        		NodeFactory.createURI("http://dbpedia.org/resource/Dublin")));
        System.out.println(realiser.realiseSentence(s4));
        s4.getSubject().setPlural(true);
        System.out.println(realiser.realiseSentence(s4));
        
        SPhraseSpec s5 = nlg.getNLForTriple(Triple.create(
        		NodeFactory.createURI("http://dbpedia.org/resource/Aero_Lloyd"), 
        		NodeFactory.createURI("http://dbpedia.org/ontology/weight"), 
        		NodeFactory.createURI("http://dbpedia.org/resource/Leipzig")));
        System.out.println(realiser.realiseSentence(s5));
//        s5.getSubject().setPlural(true);
        for (NLGElement child : s5.getSubject().getChildren()) {
			if(!child.getFeatureAsBoolean("possessive")){
				child.setPlural(true);
			}
		}
        System.out.println(realiser.realiseSentence(s5));
        
        NLGElement subject = nlgFactory.createNounPhrase("Aero Loyd");
        subject = nlgFactory.createWord("Aero Loyds", LexicalCategory.NOUN);
        subject.setFeature(Feature.POSSESSIVE, true);
        NPPhraseSpec object = nlgFactory.createNounPhrase("weight");
        object.setPlural(true);
        object.setPreModifier(subject);
        NPPhraseSpec object2 = nlgFactory.createNounPhrase("Leipzig");
        SPhraseSpec p = nlgFactory.createClause(object, "is", object2);
        System.out.println(p);
        System.out.println(realiser.realise(p));
    }
}
