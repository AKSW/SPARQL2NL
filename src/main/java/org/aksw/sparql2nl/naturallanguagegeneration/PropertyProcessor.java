/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;
import java.util.ArrayList;

import com.hp.hpl.jena.vocabulary.RDFS;

/**
 *
 * @author ngonga
 */
public class PropertyProcessor {

    public double THRESHOLD = 1.0;
    private Preposition preposition;
    WordNetDatabase database;

    public PropertyProcessor(String dictionary) {
        System.setProperty("wordnet.database.dir", dictionary);
        database = WordNetDatabase.getFileInstance();
        preposition = new Preposition(this.getClass().getClassLoader().getResourceAsStream("preposition_list.txt"));
        
    }

    public enum Type {

        VERB, NOUN, UNKNOWN;
    }

    public Type getType(String property) {
    	if(property.equals(RDFS.label.getURI())){
    		return Type.NOUN;
    	}
        property = property.trim();
        //length is > 1
        //
        if (property.contains(" ")) {
            String split[] = property.split(" ");
            //first check if the ending is a preposition
            //if yes, then the type is that of the first word
            if (preposition.isPreposition(split[split.length - 1])) {
                if (getType(split[0]) == Type.NOUN) {
                    return Type.NOUN;
                } else if (getType(split[0]) == Type.VERB) {
                    return Type.VERB;
                }
            }
            if (getType(split[split.length - 1]) == Type.NOUN) {
                return Type.NOUN;
            } else if (getType(split[0]) == Type.VERB) {
                return Type.VERB;
            } else {
                return Type.NOUN;
            }
        } else {
            double score = getScore(property);
            if (score < 0) //some count did not work
            {
                return Type.UNKNOWN;
            }
            if (score >= THRESHOLD) {
                return Type.NOUN;
            } else if (score < 1 / THRESHOLD) {
                return Type.VERB;
            } else {
                return Type.UNKNOWN;
            }
        }
    }

    /**
     * Returns log(nounCount/verbCount), i.e., positive for noun, negative for
     * verb
     *
     * @param word Input token
     * @return "Typicity"
     */
    public double getScore(String word) {
        double nounCount = 0;
        double verbCount = 0;

        Synset[] synsets = database.getSynsets(word, SynsetType.NOUN, true);
        for (int i = 0; i < synsets.length; i++) {
            String[] s = synsets[i].getWordForms();
            for (int j = 0; j < s.length; j++) {
                nounCount = nounCount + Math.log(synsets[i].getTagCount(s[j]) + 1.0);
            }
        }

        synsets = database.getSynsets(word, SynsetType.VERB, true);
        for (int i = 0; i < synsets.length; i++) {
            String[] s = synsets[i].getWordForms();
            //for (int j = 0; j < s.length; j++) {
                verbCount = verbCount + Math.log(synsets[i].getTagCount(s[0]) + 1.0);
            //}
        }
//        System.out.println("Noun count = "+nounCount);
//        System.out.println("Verb count = "+verbCount);
//        //verbCount = synsets.length;
        if (verbCount == 0 && nounCount == 0) {
            return 1.0;
        }
        if (verbCount == 0) {
            return Double.MAX_VALUE;
        }
        if (nounCount == 0) {
            return 0.0;
        } else {
            return nounCount / verbCount;
        }
    }

    public ArrayList<String> getAllSynsets(String word) {
        ArrayList<String> synset = new ArrayList<String>();

        WordNetDatabase database = WordNetDatabase.getFileInstance();
        Synset[] synsets = database.getSynsets(word, SynsetType.NOUN, true);
        for (int i = 0; i < synsets.length; i++) {
            synset.add("NOUN " + synsets[i].getWordForms()[0]);
        }
        synsets = database.getSynsets(word, SynsetType.VERB, true);
        for (int i = 0; i < synsets.length; i++) {
            synset.add("VERB " + synsets[i].getWordForms()[0]);
        }

        System.out.println(synset);
        return synset;
    }

    public String getInfinitiveForm(String word) {

        String[] split = word.split(" ");
        String verb = split[0];

        //check for past construction that simply need an auxilliary
        if (verb.endsWith("ed") || verb.endsWith("un") || verb.endsWith("wn") || verb.endsWith("en")) {
            return "be " + word;
        }

        ArrayList<String> synset = new ArrayList<String>();
        WordNetDatabase database = WordNetDatabase.getFileInstance();
        Synset[] synsets = database.getSynsets(verb, SynsetType.VERB, true);
        double min = verb.length();
        String result = verb;
        for (int i = 0; i < synsets.length; i++) {
            String[] wordForms = synsets[i].getWordForms();
            for (int j = 0; j < wordForms.length; j++) {
                if (verb.contains(wordForms[j])) {
                    result = wordForms[j];
                    if (split.length > 1) {
                        for (int k = 1; k < split.length; k++) {
                            result = result + " " + split[k];
                        }
                    }
                    return result;
                }
            }
        }
        return word;
    }

    public static void main(String args[]) {
        PropertyProcessor pp = new PropertyProcessor("resources/wordnet/dict");
        String token = "date";
        System.out.println(pp.getScore(token));
        System.out.println(pp.getType(token));
        System.out.println(pp.getAllSynsets(token));
        System.out.println(pp.getInfinitiveForm(token));
    }
}
