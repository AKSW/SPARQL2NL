
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import simplenlg.aggregation.ForwardConjunctionReductionRule;
import simplenlg.features.Feature;
import simplenlg.framework.*;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

/**
 *
 * @author christina
 */
public class Postprocessor {
    
    Lexicon lexicon;
    NLGFactory nlg;
    Realiser realiser;
    
    Set<String> primaries;
    Set<String> secondaries;
    Set<SPhraseSpec> sentences;
    Set<Set<SPhraseSpec>> unions;
    
    public Postprocessor() {
        lexicon = Lexicon.getDefaultLexicon();
        nlg = new NLGFactory(lexicon);
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
    
    public NLGElement postprocess() {

        // if it's an ASK query, simply verbalise all sentences and unions
        if (primaries.isEmpty()) 
            return verbalise(new HashSet<SPhraseSpec>(),sentences,unions);

        // otherwise
        CoordinatedPhraseElement body = nlg.createCoordinatedPhrase();
        body.setConjunction("and");
        while (!primaries.isEmpty() || !secondaries.isEmpty()) {
            body.addCoordinate(talkAboutMostImportant());
        }
        
        return body;
    }
    
    private NLGElement talkAboutMostImportant() { // most important means primary (or, if there are no primaries, secondary) with most occurrences 
        
        String current = null;
        if (!primaries.isEmpty()) {
            current = max(primaries);
            primaries.remove(current);
        }
        else if (!secondaries.isEmpty()) {
            current = max(secondaries);
            secondaries.remove(current);
        }
         
        if (current == null) return null;
        else {
            Set<SPhraseSpec> activeStore = new HashSet<SPhraseSpec>();
            Set<SPhraseSpec> passiveStore = new HashSet<SPhraseSpec>();
            Set<Set<SPhraseSpec>> unionStore = new HashSet<Set<SPhraseSpec>>();
            collectAllPhrasesContaining(current,activeStore,passiveStore,unionStore);

            NLGElement phrase = verbalise(activeStore,passiveStore,unionStore);

 //           if (phrase == null) System.out.println(" >> null");
 //           else System.out.println(" >> " + realiser.realiseSentence(phrase)); // DEBUG
            
            // remove all variables from primaries and secondaries that do not occur in remaining sentences/unions anymore
            cleanUp();
            
            return phrase;
        }
    }
    
    private NLGElement verbalise(Set<SPhraseSpec> activeStore,Set<SPhraseSpec> passiveStore,Set<Set<SPhraseSpec>> unionStore) {

        // verbalise sentences 
        Set<SPhraseSpec> sentences = new HashSet<SPhraseSpec>();
        for (SPhraseSpec s : activeStore) {
            sentences.add(s);
            // TODO check secondary information
        }
        for (SPhraseSpec s : passiveStore) {
            sentences.add(s);
            // TODO check secondary information
        }
        CoordinatedPhraseElement coord = nlg.createCoordinatedPhrase();
        coord.setConjunction("and");
        Set<SPhraseSpec> fusedsentences = fuseSubjects(fuseObjects(sentences,"and"),"and");
        for (SPhraseSpec s : fusedsentences) coord.addCoordinate(s);           
            
        // verbalise unions 
        for (Set<SPhraseSpec> union : unionStore) {
            
            Set<SPhraseSpec> unionsentences = new HashSet<SPhraseSpec>();
            for (SPhraseSpec s : union) {
                unionsentences.add(s);
                // TODO check secondary information
            }
            CoordinatedPhraseElement unioncoord = nlg.createCoordinatedPhrase();
            unioncoord.setConjunction("or");
            removeDuplicateRealisations(union);
            Set<SPhraseSpec> fusedunions = fuseSubjects(fuseObjects(unionsentences,"or"),"or");
            for (SPhraseSpec s : fusedunions) unioncoord.addCoordinate(s);
            NLGElement unionphrase = coordinate(unioncoord);
            if (unionphrase != null) coord.addCoordinate(unionphrase);
        }
            
        return coordinate(coord);
    }
    
    private void removeDuplicateRealisations(Set<SPhraseSpec> sentences) {
        
        Set<SPhraseSpec> duplicates = new HashSet<SPhraseSpec>();
        Set<String> realisations = new HashSet<String>();
        
        String realisation;
        for (SPhraseSpec s : sentences) {
            realisation = realiser.realiseSentence(s);
            if (realisations.contains(realisation)) duplicates.add(s);
            else realisations.add(realisation);
        }
        
        sentences.removeAll(duplicates);
    }
    
    private NLGElement coordinate(CoordinatedPhraseElement coord) {
        
        NLGElement phrase; 
        ForwardConjunctionReductionRule fccr = new ForwardConjunctionReductionRule();        
        if (coord.getChildren().isEmpty()) phrase = null;
        else if (coord.getChildren().size() == 1) phrase = coord.getChildren().get(0);
        else phrase = fccr.apply(coord);
        return phrase;
    }
    
    private Set<SPhraseSpec> fuseObjects(Set<SPhraseSpec> sentences,String conjunction) {

        Hashtable<String,SPhraseSpec> memory = new Hashtable<String,SPhraseSpec>();
        // String = key, NLGElement = coord.child
        
        for (SPhraseSpec sentence : sentences) {
            
            NLGElement subj = sentence.getSubject();
            NLGElement obj  = sentence.getObject();
            String key = subj.getFeatureAsString("head") + " " + realiser.realise(sentence.getVerb());
            
            // if subject and verb are the same, fuse objects
            if (memory.containsKey(key)) { 
                SPhraseSpec memelement = memory.get(key);               
                NLGElement newobj = fuse(obj,memelement.getObject(),conjunction); 
                if (newobj != null) memelement.setObject(newobj); // change memelement and don't add sentence
                else memory.put(key,sentence); // unless objects cannot be fused (i.e. newobj == null)
            }
            else memory.put(key,sentence); // otherwise add sentence
        }
        
        return new HashSet<SPhraseSpec>(memory.values());
    }
       private Set<SPhraseSpec> fuseSubjects(Set<SPhraseSpec> sentences,String conjunction) {

        Hashtable<String,SPhraseSpec> memory = new Hashtable<String,SPhraseSpec>();
        // String = key, NLGElement = coord.child
        
        for (SPhraseSpec sentence : sentences) {
            
            NLGElement subj = sentence.getSubject();
            NLGElement obj  = sentence.getObject();
            String key = realiser.realise(sentence.getVerb()) + " " + obj.getFeatureAsString("head");
            
            // if verb and object are the same, fuse subjects
            if (memory.containsKey(key)) { 
                SPhraseSpec memelement = memory.get(key);               
                NLGElement newsubj = fuse(subj,memelement.getSubject(),conjunction); 
                if (newsubj != null) memelement.setSubject(newsubj); // change memelement and don't add sentence
                else memory.put(key,sentence); // unless objects cannot be fused (i.e. newobj == null)
            }
            else memory.put(key,sentence); // otherwise add sentence
        }
        
        return new HashSet<SPhraseSpec>(memory.values());
    }
    
    private NLGElement fuse(NLGElement e1,NLGElement e2,String conjunction) {
        
        if (!e1.getCategory().equals(e2.getCategory())) return null; // cannot fuse elements of different category
        
        if (e1.getCategory().equals(PhraseCategory.NOUN_PHRASE)) {
            String[] real1 = e1.getFeatureAsString("head").split(" ");
            String[] real2 = e2.getFeatureAsString("head").split(" ");
            
            // forwards
                String prefix = "";
                int lf = 0;
                for (int i = 0; i < Math.min(real1.length,real2.length); i++) {
                    if (real1[i].equals(real2[i])) prefix += " " + real1[i];
                    else { lf = i; break; }
                } 
                prefix = prefix.trim();

                if (lf != 0) {
                    String newhead = prefix + " ";
                    for (int i = lf; i < real1.length; i++) newhead += real1[i] + " ";
                    newhead += conjunction;
                    for (int i = lf; i < real2.length; i++) newhead += " " + real2[i];
                    e1.setFeature("head",newhead);
                    return e1;
                }        
            
            // backwards   
            if (real1.length == real2.length) {
                String postfix = "";
                int lb = 0;
                for (int i = real1.length-1; i >= 0; i--) {
                    if (real1[i].equals(real2[i])) {
                        postfix = real1[i] + " " + postfix;
                        lb++;
                    }
                    else break;
                } 
                postfix = postfix.trim();

                if (lb != 0) {
                    String newhead = "";
                    for (int i = 0; i < real1.length-lb; i++) newhead += real1[i] + " ";
                    newhead += conjunction;
                    for (int i = 0; i < real2.length-lb; i++) newhead += " " + real2[i];
                    newhead += " " + postfix;
                    e1.setFeature("head",newhead);
                    return e1;
                }        
            }
        }
        
        return null;
    }
    
    private void collectAllPhrasesContaining(String var,
            Set<SPhraseSpec> activeStore,Set<SPhraseSpec> passiveStore,Set<Set<SPhraseSpec>> unionStore) {
        
        Set<SPhraseSpec> sentencesLeft = new HashSet<SPhraseSpec>();
        Set<Set<SPhraseSpec>> unionsLeft = new HashSet<Set<SPhraseSpec>>();
        
        for (SPhraseSpec sentence : sentences) {
                if (sentence.getSubject().getFeatureAsString("head").contains("?"+var)) {
                    activeStore.add(sentence);
                }
                else if (sentence.getObject().getFeatureAsString("head").contains("?"+var)) {
                    if (((WordElement) sentence.getVerb()).getBaseForm().equals("be")) {
                        NLGElement obj = sentence.getObject();
                        sentence.setObject(sentence.getSubject());
                        sentence.setSubject(obj);
                        activeStore.add(sentence);
                    } 
                    else { 
                        sentence.setFeature(Feature.PASSIVE,true);
                        passiveStore.add(sentence);
                    }
                }
                else sentencesLeft.add(sentence);
        }
        for (Set<SPhraseSpec> union : unions) {
            boolean takeit = false;
            for (SPhraseSpec sentence : union) {
                takeit = false;
                if (sentence.getSubject().getFeatureAsString("head").contains("?"+var)) takeit = true;
                else if (sentence.getObject().getFeatureAsString("head").contains("?"+var)) {
                    if (((WordElement) sentence.getVerb()).getBaseForm().equals("be")) {
                        NLGElement obj = sentence.getObject();
                        sentence.setObject(sentence.getSubject());
                        sentence.setSubject(obj);
                    } 
                    else sentence.setFeature(Feature.PASSIVE,true);
                    takeit = true;
                }
            }
            if (takeit) unionStore.add(union);
            else unionsLeft.add(union);
        }
         
        sentences = sentencesLeft;
        unions = unionsLeft;
    }
    
    private int numberOfOccurrences(String var) {
        int n = 0;
        for (SPhraseSpec s : sentences) {
            if (realiser.realiseSentence(s).contains("?"+var)) n++;
        }
        for (Set<SPhraseSpec> union : unions) {
            for (SPhraseSpec s : union) {
                if (realiser.realiseSentence(s).contains("?"+var)) n++;
            }
        }
        return n;
    }
    
    private String max(Set<String> vars) {
        String out = null;
        int beatme = 0;
        for (String s : vars) {
            if (numberOfOccurrences(s) > beatme) {
                out = s;
                beatme = numberOfOccurrences(s);
            }
        }
        return out;
    }
    
    private void cleanUp() { // very stupid Java programming
        
        Set<String> primariesLeft = new HashSet<String>();
        Set<String> secondariesLeft = new HashSet<String>();
        
        for (String var : primaries) {
            cleanUpVar(var,primariesLeft);
        }
        for (String var : secondaries) {
            cleanUpVar(var,secondariesLeft);
        }
        
        primaries = primariesLeft;
        secondaries = secondariesLeft;
    }
    private void cleanUpVar(String var,Set<String> varLeft) {
        boolean found = false;
            for (SPhraseSpec s : sentences) {
                if (realiser.realiseSentence(s).contains(var)) {
                    varLeft.add(var);
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (Set<SPhraseSpec> union : unions) {
                    for (SPhraseSpec s : union) {
                        if (realiser.realiseSentence(s).contains(var)) {
                            varLeft.add(var);
                            break;
                        }
                    }
                }
            }
    }
    
    
    public void print() {
        String maxP = max(primaries);
        String maxS = max(secondaries);
        System.out.print("\nPrimary variables: ");
        for (String s : primaries) {
            if (s.equals(maxP)) System.out.print("!");
            System.out.print(s + "("+numberOfOccurrences(s)+") ");
        }
        System.out.print("\nSecondary variables: ");
        for (String s : secondaries) {
            if (s.equals(maxS)) System.out.print("!");
            System.out.print(s + "("+numberOfOccurrences(s)+") ");
        }
        System.out.println("\nSentences: ");
        for (SPhraseSpec s : sentences) {
            System.out.println(" -- " + realiser.realiseSentence(s));
        }
        System.out.println("Unions: ");
        for (Set<SPhraseSpec> union : unions) {
            System.out.print(" -- ");
            for (SPhraseSpec s : union) {
                System.out.print(realiser.realiseSentence(s) + "\n    ");
            }
        }
        System.out.println("\n");
    }
}
