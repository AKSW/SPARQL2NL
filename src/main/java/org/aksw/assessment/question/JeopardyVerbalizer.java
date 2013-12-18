/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

import com.hp.hpl.jena.rdf.model.Resource;

import java.util.ArrayList;
import java.util.List;

import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.sparql2nl.entitysummarizer.Verbalizer;
import org.aksw.sparql2nl.entitysummarizer.gender.GenderDetector.Gender;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.sparql.SparqlEndpoint;

import simplenlg.features.Feature;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.NLGElement;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;

/**
 * Extension of Avatar for verbalizing jeopardy questions.
 * @author ngonga
 */
public class JeopardyVerbalizer extends Verbalizer {
    
    
    public JeopardyVerbalizer(SparqlEndpoint endpoint, CacheCoreEx cache, String cacheDirectory, String wordnetDirectory) {
        super(endpoint, cache, cacheDirectory, wordnetDirectory);
    }
    
    public JeopardyVerbalizer(SparqlEndpoint endpoint, String cacheDirectory, String wordnetDirectory)
    {
           super(endpoint, cacheDirectory, wordnetDirectory);
    }
    
    @Override
    public List<NPPhraseSpec> generateSubjects(Resource resource, NamedClass nc, Gender g) {
        List<NPPhraseSpec> result = new ArrayList<>();
        NPPhraseSpec np = nlg.getNPPhrase(nc.getName(), false);
        np.addPreModifier("This");
        result.add(np);
        if (g.equals(Gender.MALE)) {
            result.add(nlg.nlgFactory.createNounPhrase("he"));
        } else if (g.equals(Gender.FEMALE)) {
            result.add(nlg.nlgFactory.createNounPhrase("she"));
        } else {
            result.add(nlg.nlgFactory.createNounPhrase("it"));
        }
        return result;
    }
    
    @Override
    protected NLGElement replaceSubject(NLGElement phrase, List<NPPhraseSpec> subjects, Gender g) {
        SPhraseSpec sphrase;
        if (phrase instanceof SPhraseSpec) {
            sphrase = (SPhraseSpec) phrase;
        } else if (phrase instanceof CoordinatedPhraseElement) {
            sphrase = (SPhraseSpec) ((CoordinatedPhraseElement) phrase).getChildren().get(0);
        } else {
            return phrase;
        }
        int index = (int) Math.floor(Math.random() * subjects.size());
//        index = 2;
        if (((NPPhraseSpec) sphrase.getSubject()).getPreModifiers().size() > 0) //possessive subject
        {

            NPPhraseSpec subject = nlg.nlgFactory.createNounPhrase(((NPPhraseSpec) sphrase.getSubject()).getHead());
            NPPhraseSpec modifier;
            if (index < subjects.size() - 1) {
                modifier = nlg.nlgFactory.createNounPhrase(subjects.get(index));
                modifier.setFeature(Feature.POSSESSIVE, true);
                subject.setPreModifier(modifier);
                modifier.setFeature(Feature.POSSESSIVE, true);
            } else {
                if (g.equals(Gender.MALE)) {
                    subject.setPreModifier("his");
                } else if (g.equals(Gender.FEMALE)) {
                    subject.setPreModifier("her");
                } else {
                    subject.setPreModifier("its");
                }
            }
            if (sphrase.getSubject().isPlural()) {

//                subject.getSpecifier().setPlural(false);
                subject.setPlural(true);
            }
            sphrase.setSubject(subject);

        } else {
// does not fully work due to bug in SimpleNLG code      
            if (g.equals(Gender.MALE)) {
                sphrase.setSubject("He");
            } else if (g.equals(Gender.FEMALE)) {
                sphrase.setSubject("She");
            } else {
                sphrase.setSubject("It");
            }
        }
        return phrase;
    }
    
}
