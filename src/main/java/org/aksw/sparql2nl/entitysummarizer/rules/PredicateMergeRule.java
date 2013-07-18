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
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

/**
 * Fuses sentences s1 p1 o1 and s2 p2 o1 to s1 (p1 and p2) o1
 *
 * @author ngonga
 */
public class PredicateMergeRule implements Rule {

    /**
     * Checks whether a rule is applicable and returns the maximal number of
     * matching predicate realizations
     *
     * @param phrases List of phrases
     * @return Maximal number of mapping predicate realizations
     */
    Lexicon lexicon = Lexicon.getDefaultLexicon();
    NLGFactory nlgFactory = new NLGFactory(lexicon);
    Realiser realiser = new Realiser(lexicon);

    public int isApplicable(List<SPhraseSpec> phrases) {
        int max = 0, count;
        SPhraseSpec p1, p2;

        for (int i = 0; i < phrases.size(); i++) {
            p1 = phrases.get(i);
            count = 0;
            for (int j = i + 1; j < phrases.size(); j++) {
                p2 = phrases.get(j);
                if (p1.getObject().equals(p2.getObject()) && p1.getSubject().equals(p2.getSubject())) {
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

        SPhraseSpec p1, p2;
        String obj1, obj2;
        String subj1, subj2;
        // get mapping o's
        Multimap<Integer, Integer> map = TreeMultimap.create();
        for (int i = 0; i < phrases.size(); i++) {
            p1 = phrases.get(i);
            obj1 = realiser.realiseSentence(p1.getObject());
            subj1 = realiser.realiseSentence(p1.getSubject());

            for (int j = i + 1; j < phrases.size(); j++) {
                p2 = phrases.get(j);
                obj2 = realiser.realiseSentence(p2.getObject());
                subj2 = realiser.realiseSentence(p2.getSubject());

                if (obj1.equals(obj2) && subj1.equals(subj2)) {
                    map.put(i, j);
                }
            }
        }

        int maxSize = 0;
        int phraseIndex = -1;

        //find the index with the highest number of mappings
        for (int key: map.keySet()) {
            if (map.get(key).size() > maxSize) {
                maxSize = map.get(key).size();
                phraseIndex = key;
            }
        }
        if(phraseIndex == -1) return phrases;
        //now merge
        NLGFactory nlgFactory = new NLGFactory(Lexicon.getDefaultLexicon());
        Collection<Integer> toMerge = map.get(phraseIndex);
        toMerge.add(phraseIndex);
        CoordinatedPhraseElement elt = nlgFactory.createCoordinatedPhrase();
        for (int index : toMerge) {
            elt.addCoordinate(phrases.get(index).getVerb());
        }

        SPhraseSpec fusedPhrase = phrases.get(phraseIndex);
        fusedPhrase.setVerb(elt);
        fusedPhrase.getVerb().setPlural(true);

        //now create the final result
        List<SPhraseSpec> result = new ArrayList<SPhraseSpec>();
        result.add(fusedPhrase);
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
        s3.setVerb("hate");
        s3.setObject("apples");
        s3.getObject().setPlural(true);

        List<SPhraseSpec> phrases = new ArrayList<SPhraseSpec>();
        phrases.add(s1);
        phrases.add(s2);
        phrases.add(s3);

        for (SPhraseSpec p : phrases) {
            System.out.println("=>" + realiser.realiseSentence(p));
        }
        phrases = (new PredicateMergeRule()).apply(phrases);

        for (SPhraseSpec p : phrases) {
            System.out.println("=>" + realiser.realiseSentence(p));
        }
    }
}
