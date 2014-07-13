/**
 * 
 */
package org.aksw.sparql2nl;

import org.aksw.sparql2nl.naturallanguagegeneration.TripleConverter;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.junit.Test;

import simplenlg.features.Feature;
import simplenlg.features.Gender;
import simplenlg.features.LexicalFeature;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.lexicon.NIHDBLexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.Triple;

/**
 * @author Lorenz Buehmann
 *
 */
public class SimpleNLGTest {

	@Test
	public void testDefaultLexicon() {
		String cls = "airport";
		Lexicon lexicon = Lexicon.getDefaultLexicon();
		NLGFactory nlgFactory = new NLGFactory(lexicon);
		Realiser realiser = new Realiser(lexicon);
		NLGElement word = nlgFactory.createWord(cls, LexicalCategory.NOUN);
		NLGElement nounPhrase = nlgFactory.createNounPhrase(word);
		System.out.println(nounPhrase.getAllFeatures());
		System.out.println(nounPhrase.getRealisation());
		nounPhrase.setFeature(Feature.POSSESSIVE, true);
		nounPhrase = realiser.realise(nounPhrase);
		System.out.println(nounPhrase.getAllFeatures());
		System.out.println(nounPhrase.getRealisation());
		
		word = nlgFactory.createWord(cls, LexicalCategory.NOUN);
		nounPhrase = nlgFactory.createNounPhrase(word);
		nounPhrase = realiser.realise(nounPhrase);
		System.out.println(nounPhrase.getAllFeatures());
		System.out.println(nounPhrase.getRealisation());
	}
	
	@Test
	public void testNIHLexicon() {
		String cls = "airport";
		Lexicon lexicon = new NIHDBLexicon("/home/me/tools/lexAccess2013lite/data/HSqlDb/lexAccess2013.data");
		NLGFactory nlgFactory = new NLGFactory(lexicon);
		Realiser realiser = new Realiser(lexicon);
		NLGElement word = nlgFactory.createWord(cls, LexicalCategory.NOUN);
		NLGElement nounPhrase = nlgFactory.createNounPhrase(word);
		nounPhrase = realiser.realise(nounPhrase);
		System.out.println(nounPhrase.getAllFeatures());
		System.out.println(nounPhrase.getRealisation());
	}
	
	@Test
	public void testRelativeClause(){
		Lexicon lexicon = Lexicon.getDefaultLexicon();
		NLGFactory nlgFactory = new NLGFactory(lexicon);
		Realiser realiser = new Realiser(lexicon);
		
		SPhraseSpec cl1 = nlgFactory.createClause(null, "have", "system of government Republic");
		NPPhraseSpec np1 = nlgFactory.createNounPhrase("states");
		np1.addComplement(cl1);
		System.out.println(realiser.realise(np1));
		
		SPhraseSpec cl2 = nlgFactory.createClause(null, "be located in", nlgFactory.createClause(np1, null));
		NPPhraseSpec np2 = nlgFactory.createNounPhrase("cities");
		np2.addComplement(cl2);
		System.out.println(realiser.realise(np2));
		
		Triple t = new Triple(
				NodeFactory.createVariable("city"),
				NodeFactory.createURI("http://dbpedia.org/ontology/locatedIn"),
				NodeFactory.createVariable("state"));
		
		TripleConverter conv = new TripleConverter(SparqlEndpoint.getEndpointDBpedia(), "cache", null);
		NPPhraseSpec cl3 = conv.convertTriplePattern(t, nlgFactory.createNounPhrase("cities"), np1, true, false, false);
		System.out.println(realiser.realise(cl3));
		
		NPPhraseSpec sisterNP = nlgFactory.createNounPhrase("sister");
		NLGElement word = nlgFactory.createInflectedWord("Albert Einstein", LexicalCategory.NOUN);
		word.setFeature(LexicalFeature.PROPER, true);
		NPPhraseSpec possNP = nlgFactory.createNounPhrase(word);
		possNP.setFeature(Feature.POSSESSIVE, true);
		sisterNP.setSpecifier(possNP);
		System.out.println(realiser.realise(sisterNP));
		sisterNP.setPlural(true);
		System.out.println(realiser.realise(sisterNP));
		sisterNP.setPlural(false);
		possNP.setFeature(LexicalFeature.GENDER, Gender.MASCULINE);
        possNP.setFeature(Feature.PRONOMINAL, true);
        System.out.println(realiser.realise(sisterNP));
        possNP.setPlural(false);
        sisterNP.setPlural(true);
        System.out.println(realiser.realise(sisterNP));
        
        NPPhraseSpec np = nlgFactory.createNounPhrase(nlgFactory.createWord("darts player", LexicalCategory.NOUN));
        np.setPlural(true);
        System.out.println(realiser.realise(np));
	}

}
