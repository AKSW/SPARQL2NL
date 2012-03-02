
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import simplenlg.framework.DocumentElement;
import simplenlg.framework.NLGElement;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

/**
 *
 * @author christina
 */
public class Postprocessor {
    
    Lexicon lexicon;
    Realiser realiser;
    
    Set<String> primaries;
    Set<String> secondaries;
    Set<SPhraseSpec> sentences;
    Set<Set<SPhraseSpec>> unions;
    
    public Postprocessor() {
        lexicon = Lexicon.getDefaultLexicon();
        realiser = new Realiser(lexicon);
        primaries = new HashSet<String>();
        secondaries = new HashSet<String>();
        sentences = new HashSet<SPhraseSpec>();
        unions = new HashSet<Set<SPhraseSpec>>();
    }
    
    public void flush() {
        primaries = new HashSet<String>();
        secondaries = new HashSet<String>();
        sentences = new HashSet<SPhraseSpec>();
        unions = new HashSet<Set<SPhraseSpec>>();
    }
    
    public void addPrimary(String s) {
        primaries.add(s);
    }
    public void addSecondary(String s) {
        secondaries.add(s);
    }
    public void addSentence(SPhraseSpec sentence) {
        sentences.add(sentence);
    }
    public void addUnion(Set<SPhraseSpec> union) {
        unions.add(union);
    }
    
    public DocumentElement postprocess(DocumentElement doc) {

        
        
        return null;
    }
    
    public void print() {
        System.out.println("Primary variables: " + primaries.toString());
        System.out.println("Secondary variables: " + secondaries.toString());
        System.out.println("Sentences: ");
        for (SPhraseSpec s : sentences) {
            System.out.println(" -- " + realiser.realiseSentence(s));
        }
        System.out.println("Unions: ");
        for (Set<SPhraseSpec> union : unions) {
            System.out.print(" -- ");
            for (SPhraseSpec s : union) {
                System.out.print(realiser.realiseSentence(s));
            }
            System.out.print("\n");
        }
    }
}
