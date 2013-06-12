/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.rules;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import simplenlg.framework.CoordinatedPhraseElement;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

/**
 *
 * @author ngonga
 */
public class SubjectMergeRule {
    
    /**
     * Checks whether a rule is applicable and returns the number of pairs on
     * which it can be applied
     *
     * @param phrases List of phrases
     * @return Number of mapping pairs
     */
    Lexicon lexicon = Lexicon.getDefaultLexicon();
    NLGFactory nlgFactory = new NLGFactory(lexicon);
    Realiser realiser = new Realiser(lexicon);

    public int isApplicable(List<SPhraseSpec> phrases) {
        int count = 0;
        SPhraseSpec p1, p2;
        String subj1, subj2;

        for (int i = 0; i < phrases.size(); i++) {
            p1 = phrases.get(i);
            subj1 = p1.getSubject().getRealisation();
            for (int j = i + 1; j < phrases.size(); j++) {
                p2 = phrases.get(j);
                subj2 = p2.getSubject().getRealisation();
                if (subj1.equals(subj2)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Applies this rule to the phrases
     * 
     * @param phrases Set of phrases
     * @return Result of the rule being applied
     */
    public List<NLGElement> apply(List<SPhraseSpec> phrases) {

        SPhraseSpec p1, p2;
        String subj1, subj2;

        // get mapping o's
        Multimap<Integer, Integer> map = TreeMultimap.create();
        for (int i = 0; i < phrases.size(); i++) {
            p1 = phrases.get(i);
            subj1 = realiser.realiseSentence(p1.getSubject());

            for (int j = i + 1; j < phrases.size(); j++) {
                p2 = phrases.get(j);
                subj2 = realiser.realiseSentence(p2.getSubject());
                if (subj1.equals(subj2)) {
                    map.put(i, j);
                }
            }
        }

        int maxSize = 0;
        int phraseIndex = 0;

        //find the index with the highest number of mappings
        List<Integer> phraseIndexes = new ArrayList<Integer>(map.keySet());
        for (int key = 0; key < phraseIndexes.size(); key++) {
            if (map.get(key).size() > maxSize) {
                maxSize = map.get(key).size();
                phraseIndex = key;
            }
        }

        //now merge ** this needs to be improved
        NLGFactory nlgFactory = new NLGFactory(Lexicon.getDefaultLexicon());
        Collection<Integer> toMerge = map.get(phraseIndex);
        toMerge.add(phraseIndex);
        CoordinatedPhraseElement elt = nlgFactory.createCoordinatedPhrase();
        for (int index : toMerge) {
            elt.addCoordinate(phrases.get(index));
        }


        //now create the final result
        List<NLGElement> result = new ArrayList<NLGElement>();
        result.add(elt);
        for (int index = 0; index < phrases.size(); index++) {
            if (!toMerge.contains(index)) {
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
        s2.setVerb("eat");
        s2.setObject("apples");
        s2.getObject().setPlural(true);

        SPhraseSpec s3 = nlgFactory.createClause();
        s3.setSubject("John");
        s3.setVerb("be born in");
        s3.setObject("New York");
        s3.getObject().setPlural(true);

        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        phrases.add(s1);
        phrases.add(s2);
        phrases.add(s3);

        for (SPhraseSpec p : phrases) {
            System.out.println("=>" + realiser.realiseSentence(p));
        }
        List<NLGElement> phrases2 = (new SubjectMergeRule()).apply(phrases);

        for (NLGElement p : phrases2) {
            System.out.println("=>" + realiser.realiseSentence(p));
        }
    }
}
