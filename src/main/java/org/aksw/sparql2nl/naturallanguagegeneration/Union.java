
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.Hashtable;
import java.util.Set;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

public class Union {
    
    Set<Set<Sentence>> sentences;
    boolean optional;
    
    public Union(Set<Set<Sentence>> s,boolean o) {
        sentences = s;
        optional = o;
        if (optional) {
            for (Set<Sentence> un : sentences) {
                for (Sentence sent : un) {
                     sent.optional = true;
                }
            }
        }
    }
    
    public Sentence removeRedundancy(Realiser realiser) {
        
        Hashtable<String,Sentence> unionsents = new Hashtable<String,Sentence>();
            
        for (Set<Sentence> un : sentences) {          
             for (Sentence s : un) {
                  if (!unionsents.containsKey(realiser.realise(s.sps).toString().toLowerCase())) 
                       unionsents.put(realiser.realise(s.sps).toString().toLowerCase(),s);
             }
        }
        if (unionsents.size() == 1) { 
            for (String key : unionsents.keySet()) {
                 return unionsents.get(key);
            }
        }
        return null;
    }
    
}
