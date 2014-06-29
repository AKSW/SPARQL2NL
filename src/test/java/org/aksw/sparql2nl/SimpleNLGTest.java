/**
 * 
 */
package org.aksw.sparql2nl;

import static org.junit.Assert.*;

import org.junit.Test;

import simplenlg.features.Feature;
import simplenlg.framework.LexicalCategory;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.lexicon.NIHDBLexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.realiser.english.Realiser;

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

}
