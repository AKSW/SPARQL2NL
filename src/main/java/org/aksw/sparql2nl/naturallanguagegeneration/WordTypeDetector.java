package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.Synset;
import net.didion.jwnl.data.Word;
import net.didion.jwnl.dictionary.Dictionary;

public class WordTypeDetector {
	
	public Dictionary dict;	
	
	private static final double THRESHOLD = 0.5;
	private boolean stemWords = true;
	
	public WordTypeDetector() {
		try {
			JWNL.initialize(new FileInputStream("resources/wordnet/wordnet_properties.xml"));
			dict = Dictionary.getInstance();
		} catch (JWNLException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public boolean isNoun(String keyword) {
		System.out.println(keyword);
		try {
			String token = getFirstToken(keyword);
			
			int nrOfNounSenses = 0;
			int nrOfVerbSenses = 0;
			//get NOUN senses
			IndexWord iw;
			if(stemWords){
				iw = dict.getMorphologicalProcessor().lookupBaseForm(POS.NOUN, token);
			} else {
				iw = dict.getIndexWord(POS.NOUN, token);
			}
			if(iw != null){
				Synset[] synsets = iw.getSenses();
				nrOfNounSenses = synsets.length;
			}
			//get VERB senses
			if(stemWords){
				iw = dict.getMorphologicalProcessor().lookupBaseForm(POS.VERB, token);
			} else {
				iw = dict.getIndexWord(POS.VERB, token);
			}
			System.out.println("#Nouns: " + nrOfNounSenses);
			System.out.println("#Verbs: " + nrOfVerbSenses);
			
			double score = Math.abs(Math.log((double)nrOfVerbSenses/nrOfNounSenses));
			System.out.println("Score: " + score);
			if(score > THRESHOLD){
				return true;
			}
			
			/*
			if(nrOfNounSenses == 0 && nrOfVerbSenses != 0){
				return false;
			} else if(nrOfNounSenses != 0 && nrOfVerbSenses == 0){
				return true;
			} else if(nrOfNounSenses == 0 && nrOfVerbSenses == 0){
				return false;
			} else {
				if((double)nrOfNounSenses/nrOfVerbSenses < THRESHOLD){
					return false;
				} else if((double)nrOfVerbSenses/nrOfNounSenses < THRESHOLD){
					return true;
				}
			}*/
			
			
		} catch (JWNLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private String getFirstToken(String phrase){
		String[] tokens = phrase.split(" ");
        return tokens[0];
	}
	
	public static void main(String[] args) {
		System.out.println(new WordTypeDetector().isNoun("is"));
		System.out.println(new WordTypeDetector().isNoun("is part of"));
		System.out.println(new WordTypeDetector().isNoun("population total"));
		System.out.println(new WordTypeDetector().isNoun("star"));
		System.out.println(new WordTypeDetector().isNoun("starring"));
		System.out.println(new WordTypeDetector().isNoun("award"));
		System.out.println(new WordTypeDetector().isNoun("key"));
		System.out.println(new WordTypeDetector().isNoun("spouse"));
	}
}
