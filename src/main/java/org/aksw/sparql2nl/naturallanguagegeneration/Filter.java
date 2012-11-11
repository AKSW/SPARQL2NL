
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.HashSet;
import java.util.Set;

public class Filter {
    
    Set<Sentence> sentences;
    String coord;
    
    public Filter(Set<Sentence> s,String c) {
        sentences = s;
        coord = c;
    }
    public Filter(Sentence s) {
        sentences = new HashSet<Sentence>();
        sentences.add(s);
        coord = null;
    }
    
}
