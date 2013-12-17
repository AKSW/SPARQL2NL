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
import simplenlg.phrasespec.NPPhraseSpec;

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
    
}
