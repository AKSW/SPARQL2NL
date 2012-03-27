
package org.aksw.sparql2nl.smooth_nlg;

import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.aksw.sparql2nl.naturallanguagegeneration.boa.BoaPatternSelector;
import org.aksw.sparql2nl.naturallanguagegeneration.boa.Pattern;

import simplenlg.aggregation.ClauseCoordinationRule;
import simplenlg.features.Feature;
import simplenlg.features.NumberAgreement;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.DocumentElement;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

/**
 *
 * @author christina
 */
public class NLConstructor {
                 
    Lexicon lexicon = Lexicon.getDefaultLexicon();
    NLGFactory nlg = new NLGFactory(lexicon);
    Realiser realiser = new Realiser(lexicon);
    BoaPatternSelector boa = new BoaPatternSelector();    
    CardBox cardbox;
    
    public NLConstructor(CardBox c) {
        cardbox = c;
    }
    
    public DocumentElement construct() {
         
        SPhraseSpec head = nlg.createClause();
        head.setSubject(nlg.createNounPhrase("this","query"));
        head.setVerb("retrieve");
        
        CoordinatedPhraseElement c = nlg.createCoordinatedPhrase();
                
        for (Entity e : cardbox.primaries) {   
            NPPhraseSpec np = entity2NP(e);
            head.setObject(np);
        }
        
        System.out.println("Sentence: " + realiser.realiseSentence(head));
        
        return null;
    }
    
        
        
    private NPPhraseSpec entity2NP(Entity e) {

            String[][] m = cardbox.getMatrix(e);
            int occurrence = 1;
            
            NPPhraseSpec np; 
            if (e.count) np = nlg.createNounPhrase("the number of all",e.type);
            else np = nlg.createNounPhrase("all",e.type);
            np.setFeature(Feature.NUMBER,NumberAgreement.PLURAL);
                        
            List<NLGElement> npclauses = new ArrayList<NLGElement>();
            List<NLGElement> activeclauses = new ArrayList<NLGElement>();
            List<NLGElement> passiveclauses = new ArrayList<NLGElement>();
            
            // collect all properties into np-modifying clauses
            for (int i = 0; i < m.length; i++) {
                
                SPhraseSpec s = nlg.createClause(); 
                s.setFeature(Feature.NUMBER,NumberAgreement.PLURAL);

                List<Pattern> boapatterns = BoaPatternSelector.getNaturalLanguageRepresentation(m[i][1],1);
                if (!boapatterns.isEmpty()) {
                    s.setVerb(boapatterns.get(0));
                } else {

                    s.setVerb(queryDBpediaForLabel(m[i][1]));
                }
                if (m[i][0].replace("?","").equals(e.var)) { // then primary is subject => active sentence       
                    s.setSubject("");
                    s.setObject(buildNP(e,m[i][2],0));
                    activeclauses.add(s);
                } 
                else { // primary is object => passive sentence
                    s.setSubject(buildNP(e,m[i][0],0));
                    NPPhraseSpec obj = nlg.createNounPhrase();
                    obj.setPlural(true); 
                    obj.setRealisation("");
                    s.setObject(obj);
                    s.setFeature(Feature.PASSIVE,true);
                    passiveclauses.add(s);
                }
            }
            // ordering: first active clauses, then passive clauses
            npclauses.addAll(activeclauses);
            npclauses.addAll(passiveclauses);
            
            if (npclauses.isEmpty()) {
                return np;
            }
            // else conjoin np-modifying clauses to nptail and attach them to np as complement
            NLGElement nptail = null;
            if (npclauses.size() == 1) {
                nptail = npclauses.get(0);
            }
            else if (npclauses.size() > 1) { // then conjoin all np-modifying clauses
                ClauseCoordinationRule ccr = new ClauseCoordinationRule();  
                List<NLGElement> cs = ccr.apply(npclauses);
                if (cs.size() == 1) {
                    nptail = cs.get(0);
                }
                else { // Hack!
                    String nptailstring = " that ";
                    for (Iterator<NLGElement> it = cs.iterator(); it.hasNext();) {
                        nptailstring += realiser.realise(it.next());
                        if (it.hasNext()) nptailstring += " and that ";
                    }
                    np.setComplement(nptailstring);
                    return np;
                }
            }
            if (nptail != null) {
                nptail.setFeature(Feature.COMPLEMENTISER,"that");
                np.setComplement(nptail);
            }
            return np;
        }
        
        
    private NPPhraseSpec buildNP(Entity e,String s,int occurrence) {
   
        s = s.replace("?","");
        NPPhraseSpec np = nlg.createNounPhrase("it"); // not a good fallback!
        
        if (s.replace("?","").equals(e.var)) { // s is primary variable
            if (occurrence == 1) {
                np = nlg.createNounPhrase("the",e.type);
            } 
            else {
                np = nlg.createNounPhrase("it");
            }
            occurrence++;
        } 
        else if (cardbox.getSecondaryVars().contains(s)) { // s is secondary variable
            for (Entity sec : cardbox.secondaries) {
                if (sec.var.equals(s)) {
                    np = nlg.createNounPhrase("some",sec.type);
                    // TODO for (cardbox.filters)
                    if (!sec.properties.isEmpty()) {
                        // TODO add that clause(s)
                    } 
                    break;
                }
            }
        } 
        else { // s is resource 
            // TODO check whether it is a literal
            np = nlg.createNounPhrase(queryDBpediaForLabel(s));
        }
        
        return np;
    }
    
    private String queryDBpediaForLabel(String resource) {
         
        String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?label { " + resource + " rdfs:label ?label . Filter(lang(?label) = 'en') } ";
	QueryEngineHTTP qexec = new QueryEngineHTTP("http://live.dbpedia.org/sparql/", query);
	qexec.addDefaultGraph("http://dbpedia.org");
	ResultSet results = qexec.execSelect();

	while ( results.hasNext() ) {			
		String label = results.next().get("?label").toString();
		return label.substring(0,label.indexOf("@"));
	}
	return "N/A";    
    }
}
